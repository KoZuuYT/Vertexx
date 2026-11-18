package dev.vertexx.backup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Aplica um backup sobre os arquivos do servidor. Usa apenas classes do JDK, sem lambdas nem classes internas:
 * roda num shutdown hook, depois que o Paper desligou e o plugin foi descarregado, quando o class loader do
 * Vertexx já não carrega classes novas (por isso {@link #apply} também é chamado em modo de teste ao agendar).
 */
final class BackupRestorer {

    private static final String LOCK = "session.lock";

    private BackupRestorer() {
    }

    /** Valida o zip e, fora do modo de teste, apaga as pastas do backup e extrai tudo. Devolve null se deu certo. */
    static String apply(Path root, Path zip, List<String> roots, boolean dry) {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            byte[] buffer = new byte[65536];
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (target(root, roots, entry.getName()) == null) {
                    return "entrada inválida no zip: " + entry.getName();
                }
                if (!dry && !entry.isDirectory()) {
                    try (InputStream in = zf.getInputStream(entry)) {
                        while (in.read(buffer) >= 0) {
                            // lê tudo: confere o CRC antes de apagar qualquer coisa
                        }
                    }
                }
            }
            if (dry) {
                return null;
            }
            for (String rel : roots) {
                deleteTree(root.resolve(rel));
            }
            entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path dest = target(root, roots, entry.getName());
                if (dest == null || entry.isDirectory() || dest.getFileName().toString().equals(LOCK)) {
                    continue;
                }
                Files.createDirectories(dest.getParent());
                try (InputStream in = zf.getInputStream(entry)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                if (entry.getTime() > 0) {
                    Files.setLastModifiedTime(dest, java.nio.file.attribute.FileTime.fromMillis(entry.getTime()));
                }
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return String.valueOf(e);
        }
    }

    /** Destino seguro de uma entrada: dentro da raiz e debaixo de uma das pastas do backup. */
    private static Path target(Path root, List<String> roots, String name) {
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            return null;
        }
        String rel = root.relativize(path).toString().replace('\\', '/');
        for (String allowed : roots) {
            if (rel.equals(allowed) || rel.startsWith(allowed + "/")) {
                return path;
            }
        }
        return null;
    }

    /** Apaga o caminho; o session.lock do mundo principal (aberto pelo servidor) é o único que pode ficar. */
    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            List<Path> children;
            try (Stream<Path> stream = Files.list(path)) {
                children = stream.toList();
            }
            for (Path child : children) {
                deleteTree(child);
            }
        }
        try {
            Files.delete(path);
        } catch (IOException e) {
            boolean lock = path.getFileName().toString().equals(LOCK);
            boolean onlyLock = false;
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                onlyLock = true;
                try (Stream<Path> stream = Files.list(path)) {
                    for (Path left : stream.toList()) {
                        if (!left.getFileName().toString().equals(LOCK)) {
                            onlyLock = false;
                        }
                    }
                }
            }
            if (!lock && !onlyLock) {
                throw e;
            }
        }
    }

    /** Espera o servidor soltar o session.lock do mundo principal (sinal de que terminou de salvar e fechar). */
    private static boolean waitServerStop(Path lockFile, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (true) {
            if (!Files.exists(lockFile)) {
                return true;
            }
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock != null) {
                    return true;
                }
            } catch (OverlappingFileLockException | IOException e) {
                // ainda em uso pelo servidor
            }
            if (System.currentTimeMillis() > end) {
                return false;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    private static void log(Path logFile, String text) {
        String line = "[" + LocalDateTime.now() + "] " + text + System.lineSeparator();
        System.out.print("[Vertexx] " + text + System.lineSeparator());
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // sem log em arquivo; o console já recebeu
        }
    }

    /** Corpo do shutdown hook: espera o desligamento, aplica o backup e remove o marcador pendente. */
    static void runAfterShutdown(Path root, Path zip, List<String> roots, Path lockFile, Path pending, Path logFile) {
        if (!Files.exists(pending)) {
            return;
        }
        log(logFile, "Aguardando o servidor terminar de desligar para restaurar " + zip.getParent().getFileName() + "...");
        if (!waitServerStop(lockFile, 180_000L)) {
            log(logFile, "O servidor não soltou os arquivos a tempo; a restauração ocorrerá na próxima inicialização.");
            return;
        }
        String error = apply(root, zip, roots, false);
        if (error == null) {
            try {
                Files.deleteIfExists(pending);
            } catch (IOException ignored) {
                // o marcador restante será tratado (e removido) na próxima inicialização
            }
            log(logFile, "Backup restaurado com sucesso. Pode iniciar o servidor.");
        } else {
            log(logFile, "FALHA ao restaurar: " + error + ". Use o backup de segurança, se existir.");
            try {
                Files.deleteIfExists(pending);
            } catch (IOException ignored) {
                // sem ação
            }
        }
    }

    /** Fallback na inicialização (onLoad), caso o hook não tenha rodado. Devolve null se deu certo. */
    static String applyAtStartup(Path root, Path zip, List<String> roots, Path pending, Path logFile) {
        log(logFile, "Restauração pendente encontrada na inicialização: aplicando " + zip.getParent().getFileName() + ".");
        String error = apply(root, zip, roots, false);
        try {
            Files.deleteIfExists(pending);
        } catch (IOException ignored) {
            // sem ação
        }
        log(logFile, error == null ? "Backup restaurado (na inicialização)." : "FALHA ao restaurar: " + error);
        return error;
    }
}

package dev.vertexx.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;

/**
 * Projetos das duas salas de boss. Tudo é descrito em coordenadas locais (lx para a direita, lz para a
 * frente, onde fica a entrada) e girado para a direção escolhida, então a mesma estrutura serve em qualquer
 * orientação.
 */
final class Structures {

    private Structures() {
    }

    /** Conversão local → mundo para uma entrada voltada para (fx, fz). */
    private record Frame(BlockQueue q, int cx, int cz, int fx, int fz) {
        int x(int lx, int lz) {
            return cx + fx * lz + fz * lx;
        }

        int z(int lx, int lz) {
            return cz + fz * lz - fx * lx;
        }

        void put(int lx, int y, int lz, Material material) {
            q.set(x(lx, lz), y, z(lx, lz), material);
        }

        void box(int lx1, int lz1, int lx2, int lz2, int y1, int y2, Material material) {
            for (int lx = Math.min(lx1, lx2); lx <= Math.max(lx1, lx2); lx++) {
                for (int lz = Math.min(lz1, lz2); lz <= Math.max(lz1, lz2); lz++) {
                    for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                        put(lx, y, lz, material);
                    }
                }
            }
        }

        int[] world(int lx, int y, int lz) {
            return new int[] {x(lx, lz), y, z(lx, lz)};
        }
    }

    // ── Castelo de Gelo (Iceologer) ────────────────────────────

    /**
     * Castelo de gelo: muralha com quatro torres, pátio, e um torreão central cujo salão é a sala do boss.
     *
     * @param lowY   altura do ponto mais baixo do terreno (a fundação desce até lá)
     * @param ramp   comprimento da rampa de acesso que desce do portão até o terreno
     * @return posições do selo da porta do torreão
     */
    static List<int[]> iceCastle(BlockQueue q, int cx, int cz, int fy, int fx, int fz, int lowY, int ramp) {
        Frame f = new Frame(q, cx, cz, fx, fz);
        Random random = new Random(cx * 31L + cz * 17L);

        // limpa o volume (árvores, neve, relevo)
        for (int lx = -25; lx <= 25; lx++) {
            for (int lz = -25; lz <= 25; lz++) {
                for (int y = fy; y <= fy + 38; y++) {
                    f.put(lx, y, lz, Material.AIR);
                }
            }
        }
        // fundação e piso do pátio
        for (int lx = -23; lx <= 23; lx++) {
            for (int lz = -23; lz <= 23; lz++) {
                for (int y = lowY - 1; y < fy - 1; y++) {
                    f.put(lx, y, lz, Material.PACKED_ICE);
                }
                if (Math.abs(lx) <= 21 && Math.abs(lz) <= 21) {
                    boolean path = Math.abs(lx) <= 2 && lz >= 10;
                    Material floor = path ? Material.BLUE_ICE
                            : (lx & 3) == 0 && (lz & 3) == 0 ? Material.PACKED_ICE : Material.SNOW_BLOCK;
                    f.put(lx, fy - 1, lz, floor);
                } else {
                    f.put(lx, fy - 1, lz, Material.PACKED_ICE);
                }
            }
        }
        // rampa de acesso até o terreno
        for (int i = 0; i < ramp; i++) {
            for (int lx = -2; lx <= 2; lx++) {
                for (int y = lowY - 1; y <= fy - 1 - i; y++) {
                    f.put(lx, y, 22 + i + 1, y == fy - 1 - i ? Material.BLUE_ICE : Material.PACKED_ICE);
                }
            }
        }

        // muralha
        for (int lx = -21; lx <= 21; lx++) {
            for (int lz = -21; lz <= 21; lz++) {
                int m = Math.max(Math.abs(lx), Math.abs(lz));
                if (m < 19) {
                    continue;
                }
                for (int y = fy; y <= fy + 8; y++) {
                    f.put(lx, y, lz, y == fy + 3 || y == fy + 7 ? Material.BLUE_ICE : Material.PACKED_ICE);
                }
                if (m == 21 && ((lx + lz) & 1) == 0) {
                    f.put(lx, fy + 9, lz, Material.PACKED_ICE);
                }
            }
        }
        // portão
        f.box(-2, 19, 2, 21, fy, fy + 5, Material.AIR);
        f.box(-3, 19, 3, 21, fy + 6, fy + 6, Material.BLUE_ICE);
        f.box(-3, 19, -3, 21, fy, fy + 5, Material.QUARTZ_BRICKS);
        f.box(3, 19, 3, 21, fy, fy + 5, Material.QUARTZ_BRICKS);
        f.put(-3, fy + 3, 21, Material.SEA_LANTERN);
        f.put(3, fy + 3, 21, Material.SEA_LANTERN);
        // seteiras
        for (int pos : new int[] {-14, -8, 8, 14}) {
            f.box(pos, 19, pos, 21, fy + 4, fy + 5, Material.LIGHT_BLUE_STAINED_GLASS);
            f.box(-21, pos, -19, pos, fy + 4, fy + 5, Material.LIGHT_BLUE_STAINED_GLASS);
            f.box(19, pos, 21, pos, fy + 4, fy + 5, Material.LIGHT_BLUE_STAINED_GLASS);
            if (pos < -8 || pos > 8) {
                f.box(pos, -21, pos, -19, fy + 4, fy + 5, Material.LIGHT_BLUE_STAINED_GLASS);
            }
        }

        // torres de canto
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                tower(f, sx * 20, sz * 20, fy);
            }
        }

        keep(f, fy, random);
        courtyard(f, fy, random);

        List<int[]> seals = new ArrayList<>();
        for (int lx = -2; lx <= 2; lx++) {
            for (int y = fy; y <= fy + 6; y++) {
                seals.add(f.world(lx, y, 10));
            }
        }
        return seals;
    }

    private static void tower(Frame f, int tx, int tz, int fy) {
        // interior oco, parede de gelo e cúpula cônica
        for (int y = fy; y <= fy + 17; y++) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d <= 3.0) {
                        f.put(tx + dx, y, tz + dz, Material.AIR);
                    } else if (d <= 4.6) {
                        boolean band = y == fy + 4 || y == fy + 9 || y == fy + 14;
                        f.put(tx + dx, y, tz + dz, band ? Material.BLUE_ICE : Material.PACKED_ICE);
                    }
                }
            }
        }
        for (int[] w : new int[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
            f.box(tx + w[0], tz + w[1], tx + w[0], tz + w[1], fy + 10, fy + 12, Material.LIGHT_BLUE_STAINED_GLASS);
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.hypot(dx, dz) <= 3.2) {
                    f.put(tx + dx, fy - 1, tz + dz, Material.QUARTZ_BRICKS);
                }
            }
        }
        for (int k = 0; k < 12; k++) {
            double radius = 5.2 * (1 - k / 12.0);
            Material material = k % 3 == 2 ? Material.BLUE_ICE : Material.PACKED_ICE;
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (Math.hypot(dx, dz) <= radius) {
                        f.put(tx + dx, fy + 18 + k, tz + dz, material);
                    }
                }
            }
        }
        f.put(tx, fy + 30, tz, Material.SEA_LANTERN);
        f.put(tx, fy + 31, tz, Material.BLUE_ICE);
        f.put(tx, fy + 32, tz, Material.BLUE_ICE);
    }

    private static void keep(Frame f, int fy, Random random) {
        // paredes duplas do torreão
        for (int lx = -11; lx <= 11; lx++) {
            for (int lz = -11; lz <= 11; lz++) {
                int m = Math.max(Math.abs(lx), Math.abs(lz));
                if (m < 10) {
                    continue;
                }
                boolean corner = Math.abs(lx) >= 10 && Math.abs(lz) >= 10;
                for (int y = fy; y <= fy + 20; y++) {
                    Material material = corner ? Material.BLUE_ICE
                            : y == fy + 9 || y == fy + 16 ? Material.QUARTZ_BRICKS : Material.PACKED_ICE;
                    f.put(lx, y, lz, material);
                }
            }
        }
        // teto e telhado em pirâmide
        f.box(-11, -11, 11, 11, fy + 17, fy + 18, Material.PACKED_ICE);
        for (int k = 0; k < 13; k++) {
            int half = 12 - k;
            f.box(-half, -half, half, half, fy + 19 + k, fy + 19 + k, k % 3 == 2 ? Material.BLUE_ICE : Material.PACKED_ICE);
        }
        for (int y = fy + 32; y <= fy + 36; y++) {
            f.put(0, y, 0, y == fy + 36 ? Material.SEA_LANTERN : Material.BLUE_ICE);
        }
        // porta, arco e lanternas
        f.box(-2, 10, 2, 11, fy, fy + 6, Material.AIR);
        f.box(-3, 10, 3, 11, fy + 7, fy + 7, Material.BLUE_ICE);
        f.put(-3, fy + 4, 11, Material.SEA_LANTERN);
        f.put(3, fy + 4, 11, Material.SEA_LANTERN);
        // janelas altas e luzes embutidas nas paredes do salão
        for (int pos : new int[] {-6, 6}) {
            f.box(pos, 10, pos, 11, fy + 8, fy + 13, Material.LIGHT_BLUE_STAINED_GLASS);
            f.box(pos, -11, pos, -10, fy + 8, fy + 13, Material.LIGHT_BLUE_STAINED_GLASS);
            f.box(10, pos, 11, pos, fy + 8, fy + 13, Material.LIGHT_BLUE_STAINED_GLASS);
            f.box(-11, pos, -10, pos, fy + 8, fy + 13, Material.LIGHT_BLUE_STAINED_GLASS);
        }
        for (int pos : new int[] {-3, 3}) {
            f.put(pos, fy + 4, -10, Material.SEA_LANTERN);
            f.put(-10, fy + 4, pos, Material.SEA_LANTERN);
            f.put(10, fy + 4, pos, Material.SEA_LANTERN);
        }

        // piso do salão: xadrez de gelo e círculos de runas
        for (int lx = -9; lx <= 9; lx++) {
            for (int lz = -9; lz <= 9; lz++) {
                double d = Math.hypot(lx, lz);
                Material floor = ((lx + lz) & 1) == 0 ? Material.BLUE_ICE : Material.PACKED_ICE;
                if (d <= 1.3) {
                    floor = Material.SEA_LANTERN;
                } else if (d <= 3.4) {
                    floor = Material.QUARTZ_BRICKS;
                } else if (d <= 4.4) {
                    floor = Material.LIGHT_BLUE_CONCRETE;
                } else if (d >= 6.3 && d <= 7.2) {
                    floor = Material.LIGHT_BLUE_CONCRETE;
                }
                f.put(lx, fy - 1, lz, floor);
            }
        }
        // pilares de cobertura
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int x1 = sx > 0 ? 5 : -6;
                int z1 = sz > 0 ? 5 : -6;
                f.box(x1, z1, x1 + 1, z1 + 1, fy, fy + 16, Material.BLUE_ICE);
                f.box(x1, z1, x1 + 1, z1 + 1, fy, fy + 1, Material.QUARTZ_BRICKS);
                f.box(x1, z1, x1 + 1, z1 + 1, fy + 15, fy + 16, Material.QUARTZ_BRICKS);
                f.put(sx > 0 ? 5 : -5, fy + 6, sz > 0 ? 5 : -5, Material.SEA_LANTERN);
            }
        }
        // lanternas no teto e pingentes de gelo
        for (int lx = -6; lx <= 6; lx += 6) {
            for (int lz = -6; lz <= 6; lz += 6) {
                f.put(lx, fy + 17, lz, Material.SEA_LANTERN);
            }
        }
        for (int lx = -9; lx <= 9; lx++) {
            for (int lz = -9; lz <= 9; lz++) {
                if (Math.hypot(lx, lz) < 4.5 || Math.abs(lx) >= 5 && Math.abs(lx) <= 6 && Math.abs(lz) >= 5
                        && Math.abs(lz) <= 6) {
                    continue;
                }
                if (random.nextDouble() < 0.06) {
                    int length = 1 + random.nextInt(3);
                    for (int i = 0; i < length; i++) {
                        f.put(lx, fy + 16 - i, lz, Material.PACKED_ICE);
                    }
                }
            }
        }
    }

    private static void courtyard(Frame f, int fy, Random random) {
        // postes de luz ao longo do caminho
        for (int lz : new int[] {13, 17}) {
            for (int lx : new int[] {-3, 3}) {
                f.box(lx, lz, lx, lz, fy, fy + 3, Material.PACKED_ICE);
                f.put(lx, fy + 4, lz, Material.SEA_LANTERN);
            }
        }
        // formações de gelo decorativas
        int[][] spots = {{-15, -15}, {15, -15}, {-15, 15}, {15, 15}, {-15, 2}, {15, 2}, {-8, -15}, {8, -15}};
        for (int[] spot : spots) {
            int height = 5 + random.nextInt(4);
            for (int k = 0; k < height; k++) {
                Material material = k > height - 3 ? Material.BLUE_ICE : Material.PACKED_ICE;
                if (k < height / 2) {
                    f.box(spot[0], spot[1], spot[0] + 1, spot[1] + 1, fy + k, fy + k, material);
                } else {
                    f.put(spot[0], fy + k, spot[1], material);
                }
            }
        }
    }

    // ── Câmara do Wildfire (fortaleza do Nether) ───────────────

    /**
     * Câmara circular de tijolos do Nether com cúpula e um corredor que sai até a fortaleza.
     *
     * @param corridorEnd distância (a partir do centro) até onde o corredor entra na fortaleza
     * @return posições do selo do corredor
     */
    static List<int[]> wildfireHall(BlockQueue q, int cx, int cz, int fy, int fx, int fz, int corridorEnd) {
        Frame f = new Frame(q, cx, cz, fx, fz);
        Random random = new Random(cx * 13L + cz * 7L);

        // volume livre
        for (int dy = 0; dy <= 27; dy++) {
            q.disk(cx, cz, fy + dy, 16.5, Material.AIR);
        }
        // base em forma de ilha flutuante
        for (int k = 1; k <= 11; k++) {
            double radius = Math.max(2, 15 - k * 1.3);
            for (int lx = -15; lx <= 15; lx++) {
                for (int lz = -15; lz <= 15; lz++) {
                    if (Math.hypot(lx, lz) <= radius) {
                        f.put(lx, fy - 3 - k, lz, random.nextInt(4) == 0 ? Material.NETHERRACK : Material.BLACKSTONE);
                    }
                }
            }
        }
        q.cylinder(cx, cz, fy - 3, fy - 2, 15, Material.NETHER_BRICKS);
        // piso com anéis e raios
        q.disk(cx, cz, fy - 1, 15, Material.NETHER_BRICKS);
        q.ring(cx, cz, fy - 1, 11, 12.4, Material.RED_NETHER_BRICKS);
        q.ring(cx, cz, fy - 1, 6.4, 7.6, Material.CHISELED_NETHER_BRICKS);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            for (double r = 3.6; r <= 6.2; r += 0.5) {
                f.put((int) Math.round(Math.cos(a) * r), fy - 1, (int) Math.round(Math.sin(a) * r), Material.RED_NETHER_BRICKS);
            }
            for (double r = 7.8; r <= 11; r += 0.5) {
                f.put((int) Math.round(Math.cos(a) * r), fy - 1, (int) Math.round(Math.sin(a) * r), Material.RED_NETHER_BRICKS);
            }
            double b = a + Math.PI / 8;
            f.put((int) Math.round(Math.cos(b) * 9.5), fy - 1, (int) Math.round(Math.sin(b) * 9.5), Material.SHROOMLIGHT);
            f.put((int) Math.round(Math.cos(a) * 7), fy - 1, (int) Math.round(Math.sin(a) * 7), Material.SHROOMLIGHT);
        }
        q.disk(cx, cz, fy - 1, 3.2, Material.POLISHED_BLACKSTONE_BRICKS);
        q.disk(cx, cz, fy - 1, 1.7, Material.GILDED_BLACKSTONE);
        q.set(cx, fy - 1, cz, Material.CRYING_OBSIDIAN);

        // paredes cilíndricas com faixas vermelhas
        for (int y = fy; y <= fy + 11; y++) {
            Material band = y == fy + 4 || y == fy + 10 ? Material.RED_NETHER_BRICKS : Material.NETHER_BRICKS;
            q.ring(cx, cz, y, 13, 15, band);
        }
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            if (i % 2 == 0) {
                q.set((int) Math.round(cx + Math.cos(a) * 13.4), fy + 7, (int) Math.round(cz + Math.sin(a) * 13.4),
                        Material.SHROOMLIGHT);
            }
        }
        // cúpula
        for (int t = 0; t <= 14; t++) {
            double outer = 15 * Math.sqrt(Math.max(0, 1 - (t / 15.0) * (t / 15.0)));
            double inner = t <= 12 ? 13 * Math.sqrt(Math.max(0, 1 - (t / 13.0) * (t / 13.0))) : -1;
            Material material = t % 4 == 3 ? Material.RED_NETHER_BRICKS : Material.NETHER_BRICKS;
            if (inner > 0.5) {
                q.ring(cx, cz, fy + 12 + t, inner, outer, material);
            } else {
                q.disk(cx, cz, fy + 12 + t, outer, material);
            }
        }
        q.set(cx, fy + 26, cz, Material.SHROOMLIGHT);

        // pilastras ao redor
        for (int k = 0; k < 8; k++) {
            if (k == 2) {
                continue; // direção da entrada
            }
            double a = Math.PI / 4 * k;
            int px = (int) Math.round(Math.cos(a) * 10.6);
            int pz = (int) Math.round(Math.sin(a) * 10.6);
            f.box(px, pz, px + 1, pz + 1, fy, fy + 11, Material.NETHER_BRICKS);
            f.box(px, pz, px + 1, pz + 1, fy, fy + 1, Material.RED_NETHER_BRICKS);
            f.box(px, pz, px + 1, pz + 1, fy + 10, fy + 11, Material.RED_NETHER_BRICKS);
            f.put(px, fy + 6, pz, Material.SHROOMLIGHT);
        }
        // blocos de cobertura com braseiros
        int[][] cover = {{-6, -4, 5}, {6, -4, 4}, {-7, 3, 6}, {7, 3, 4}};
        for (int i = 0; i < cover.length; i++) {
            int[] c = cover[i];
            f.box(c[0], c[1], c[0] + 1, c[1] + 1, fy, fy + c[2] - 1, Material.POLISHED_BLACKSTONE_BRICKS);
            f.box(c[0], c[1], c[0] + 1, c[1] + 1, fy + c[2], fy + c[2], Material.GILDED_BLACKSTONE);
            if (i % 2 == 0) {
                f.put(c[0], fy + c[2] + 1, c[1], Material.CAMPFIRE);
            }
        }

        // corredor até a fortaleza
        int shellEnd = corridorEnd - 3;
        f.box(-4, 13, 4, shellEnd, fy - 2, fy + 7, Material.NETHER_BRICKS);
        f.box(-2, 12, 2, corridorEnd, fy, fy + 5, Material.AIR);
        f.box(-1, 14, 1, corridorEnd, fy - 1, fy - 1, Material.RED_NETHER_BRICKS);
        for (int lz = 17; lz <= shellEnd; lz += 5) {
            f.put(-3, fy + 3, lz, Material.SHROOMLIGHT);
            f.put(3, fy + 3, lz, Material.SHROOMLIGHT);
        }
        f.box(-3, 13, 3, 15, fy + 6, fy + 6, Material.RED_NETHER_BRICKS);

        List<int[]> seals = new ArrayList<>();
        for (int lx = -2; lx <= 2; lx++) {
            for (int y = fy; y <= fy + 5; y++) {
                seals.add(f.world(lx, y, 14));
            }
        }
        return seals;
    }

    // ── Pináculo de Quitina (Void-Scarab, Abismos de Enderita) ──

    /** Raio interno da câmara em cada altura (relativa ao piso): cilindro de 14 e cúpula elíptica de 10 acima de 14. */
    private static double spireInner(int dy) {
        if (dy <= 14) {
            return 14;
        }
        double t = (dy - 14) / 10.0;
        return t >= 1 ? 0 : 14 * Math.sqrt(1 - t * t);
    }

    /** Raio externo da torre: tambor de 16,5 até 14 de altura e depois um cone até a ponta (42 blocos). */
    private static double spireOuter(int dy) {
        if (dy <= 14) {
            return 16.5;
        }
        return Math.max(2, 16.5 - 14.5 * ((dy - 14) / 28.0));
    }

    /**
     * Torre-colmeia cônica de 42 blocos com a Câmara da Matriarca (28 de diâmetro), ilha de base, quatro pontes e o
     * Altar de Cristal do Vazio no centro. A entrada fica em +lz.
     *
     * @return posições do selo da entrada
     */
    static List<int[]> chitinSpire(BlockQueue q, int cx, int cz, int fy, int fx, int fz) {
        Frame f = new Frame(q, cx, cz, fx, fz);
        Random random = new Random(cx * 31L + cz * 17L);
        Material[] shell = {Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICKS, Material.POLISHED_DEEPSLATE,
                Material.CRACKED_DEEPSLATE_BRICKS, Material.CRYING_OBSIDIAN, Material.OBSIDIAN};

        // ilha de base afunilada, para a torre nunca ficar no ar
        for (int k = 1; k <= 14; k++) {
            double radius = Math.max(2, 19 - k * 1.25);
            q.disk(cx, cz, fy - 1 - k, radius, random.nextInt(5) == 0 ? Material.BLACKSTONE : Material.END_STONE);
        }
        q.cylinder(cx, cz, fy - 3, fy - 2, 18, Material.END_STONE_BRICKS);
        // volume livre acima do piso
        for (int dy = 0; dy <= 44; dy++) {
            q.disk(cx, cz, fy + dy, 17.5, Material.AIR);
        }
        // casca: tambor e cone sólidos, depois a câmara oca por dentro
        for (int dy = 0; dy <= 42; dy++) {
            double outer = spireOuter(dy);
            for (int lx = -18; lx <= 18; lx++) {
                for (int lz = -18; lz <= 18; lz++) {
                    if (Math.hypot(lx, lz) <= outer) {
                        f.put(lx, fy + dy, lz, shell[random.nextInt(shell.length)]);
                    }
                }
            }
        }
        for (int dy = 0; dy <= 24; dy++) {
            double inner = spireInner(dy);
            if (inner > 0.5) {
                q.disk(cx, cz, fy + dy, inner, Material.AIR);
            }
        }
        // anéis claros de End Stone Bricks no cone
        for (int dy = 15; dy <= 42; dy += 6) {
            q.ring(cx, cz, fy + dy, spireOuter(dy) - 1, spireOuter(dy), Material.END_STONE_BRICKS);
        }
        q.set(cx, fy + 43, cz, Material.AMETHYST_BLOCK);
        q.set(cx, fy + 44, cz, Material.AMETHYST_CLUSTER);
        q.set(cx, fy + 45, cz, Material.END_ROD);

        // nervuras exteriores em forma de patas de inseto ancorando a torre
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k + Math.PI / 8;
            for (int dy = -3; dy <= 16; dy++) {
                double t = Math.max(0, 1 - dy / 14.0);
                double r = 16.5 + 6 * t * t;
                int px = (int) Math.round(cx + Math.cos(a) * r);
                int pz = (int) Math.round(cz + Math.sin(a) * r);
                q.set(px, fy + dy, pz, Material.END_STONE_BRICKS);
                q.set(px + 1, fy + dy, pz, Material.END_STONE_BRICKS);
                q.set(px, fy + dy, pz + 1, Material.END_STONE_BRICKS);
            }
        }
        // janelas âmbar e verde-ácido (vidro sobre luz): no tambor a luz entra; no cone é só enfeite
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            for (int dy : new int[] {7, 11, 20, 27, 34}) {
                double r = spireOuter(dy) - 0.5;
                Material glass = (k + dy) % 2 == 0 ? Material.ORANGE_STAINED_GLASS : Material.LIME_STAINED_GLASS;
                for (int w = -1; w <= 1; w++) {
                    double aa = a + w * (dy < 15 ? 0.07 : 0.12);
                    int px = (int) Math.round(cx + Math.cos(aa) * r);
                    int pz = (int) Math.round(cz + Math.sin(aa) * r);
                    for (int h = 0; h <= (dy < 15 ? 3 : 1); h++) {
                        q.set(px, fy + dy + h, pz, glass);
                        if (dy >= 15) {
                            q.set((int) Math.round(cx + Math.cos(aa) * (r - 1.5)), fy + dy + h,
                                    (int) Math.round(cz + Math.sin(aa) * (r - 1.5)), Material.SHROOMLIGHT);
                        }
                    }
                }
            }
        }

        // piso: tijolos de ardósia com anéis
        q.disk(cx, cz, fy - 1, 14.5, Material.DEEPSLATE_TILES);
        q.ring(cx, cz, fy - 1, 10, 11.4, Material.CRACKED_DEEPSLATE_TILES);
        q.ring(cx, cz, fy - 1, 5.4, 6.4, Material.CHISELED_DEEPSLATE);
        // poças de ácido (rasas): manchas de concreto-pó verde no piso, longe da entrada
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 7.5 + random.nextDouble() * 4;
            int px = (int) Math.round(Math.cos(a) * r);
            int pz = (int) Math.round(Math.sin(a) * r);
            if (Math.abs(a - Math.PI / 2) < 0.4) {
                continue;
            }
            for (int lx = -2; lx <= 2; lx++) {
                for (int lz = -2; lz <= 2; lz++) {
                    if (lx * lx + lz * lz <= 4 + random.nextInt(2)) {
                        q.set(cx + px + lx, fy - 1, cz + pz + lz, Material.LIME_CONCRETE_POWDER);
                    }
                }
            }
        }
        // ninho elevado com o altar de cristal
        q.disk(cx, cz, fy, 3.2, Material.PURPUR_BLOCK);
        q.disk(cx, cz, fy, 2.2, Material.END_STONE_BRICKS);
        q.set(cx, fy + 1, cz, Material.END_PORTAL_FRAME);
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k + Math.PI / 4;
            int px = (int) Math.round(cx + Math.cos(a) * 1.5);
            int pz = (int) Math.round(cz + Math.sin(a) * 1.5);
            q.set(px, fy + 1, pz, Material.AMETHYST_CLUSTER);
            q.set(px, fy + 2, pz, Material.END_ROD);
        }

        // costelas arqueadas de End Stone Bricks acompanhando a cúpula
        for (int k = 0; k < 12; k++) {
            if (k == 3) {
                continue;
            }
            double a = Math.PI / 6 * k;
            for (int dy = 0; dy <= 23; dy++) {
                double r = spireInner(dy) - 0.5;
                if (r < 1) {
                    break;
                }
                q.set((int) Math.round(cx + Math.cos(a) * r), fy + dy, (int) Math.round(cz + Math.sin(a) * r),
                        Material.END_STONE_BRICKS);
                q.set((int) Math.round(cx + Math.cos(a) * (r - 1)), fy + dy, (int) Math.round(cz + Math.sin(a) * (r - 1)),
                        dy % 5 == 4 ? Material.SHROOMLIGHT : Material.END_STONE_BRICKS);
            }
        }
        // teias e casulos bioluminescentes pendurados no teto
        for (int i = 0; i < 26; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 3 + random.nextDouble() * 8.5;
            int px = (int) Math.round(cx + Math.cos(a) * r);
            int pz = (int) Math.round(cz + Math.sin(a) * r);
            int ceiling = fy + 14 + (int) Math.floor(10 * Math.sqrt(Math.max(0, 1 - (r / 14.0) * (r / 14.0)))) - 1;
            int drop = 2 + random.nextInt(4);
            for (int h = 0; h < drop; h++) {
                q.set(px, ceiling - h, pz, Material.COBWEB);
            }
            if (i % 2 == 0) {
                q.set(px, ceiling - drop, pz, Material.SHROOMLIGHT);
                q.set(px, ceiling - drop - 1, pz, i % 4 == 0 ? Material.PEARLESCENT_FROGLIGHT : Material.OCHRE_FROGLIGHT);
            } else {
                q.set(px, ceiling, pz, Material.GLOW_LICHEN);
            }
        }

        // quatro pontes estreitas sobre o abismo; a de +lz é a entrada
        for (int side = 0; side < 4; side++) {
            int dx = new int[] {0, 1, 0, -1}[side];
            int dz = new int[] {1, 0, -1, 0}[side];
            for (int d = 14; d <= 44; d++) {
                for (int w = -1; w <= 1; w++) {
                    f.put(dx * d + dz * w, fy - 1, dz * d + dx * w,
                            d % 6 == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_TILES);
                    for (int h = 0; h < 5; h++) {
                        f.put(dx * d + dz * w, fy + h, dz * d + dx * w, Material.AIR);
                    }
                }
                if (d % 4 == 0) {
                    f.put(dx * d + dz * 2, fy, dz * d + dx * 2, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                    f.put(dx * d - dz * 2, fy, dz * d - dx * 2, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                    f.put(dx * d + dz * 2, fy + 1, dz * d + dx * 2, Material.END_ROD);
                    f.put(dx * d - dz * 2, fy + 1, dz * d - dx * 2, Material.END_ROD);
                }
            }
        }
        // porta da entrada atravessando o tambor
        f.box(-2, 12, 2, 18, fy, fy + 5, Material.AIR);
        f.box(-3, 14, 3, 17, fy + 6, fy + 6, Material.CRYING_OBSIDIAN);
        f.box(-3, 14, -3, 17, fy, fy + 5, Material.END_STONE_BRICKS);
        f.box(3, 14, 3, 17, fy, fy + 5, Material.END_STONE_BRICKS);
        f.put(-3, fy + 3, 18, Material.SHROOMLIGHT);
        f.put(3, fy + 3, 18, Material.SHROOMLIGHT);
        List<int[]> seals = new ArrayList<>();
        for (int lx = -2; lx <= 2; lx++) {
            for (int y = fy; y <= fy + 5; y++) {
                seals.add(f.world(lx, y, 15));
            }
        }
        return seals;
    }
}

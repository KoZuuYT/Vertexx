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

        void put(int lx, int y, int lz, org.bukkit.block.data.BlockData data) {
            q.set(x(lx, lz), y, z(lx, lz), data);
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

    private static final double SPIRE_IN = 24;
    private static final double SPIRE_OUT = 26.5;
    private static final int SPIRE_WALL = 20;
    private static final int SPIRE_DOME = 14;
    private static final int SPIRE_TOP = 52;

    /** Raio interno da câmara em cada altura (relativa ao piso): cilindro de 24 e cúpula elíptica de 14 acima dele. */
    private static double spireInner(int dy) {
        if (dy <= SPIRE_WALL) {
            return SPIRE_IN;
        }
        double t = (dy - SPIRE_WALL) / (double) SPIRE_DOME;
        return t >= 1 ? 0 : SPIRE_IN * Math.sqrt(1 - t * t);
    }

    /** Raio externo da torre: tambor de 26,5 até 20 de altura e depois um cone até a ponta (52 blocos). */
    private static double spireOuter(int dy) {
        if (dy <= SPIRE_WALL) {
            return SPIRE_OUT;
        }
        return Math.max(2, SPIRE_OUT - (SPIRE_OUT - 2) * ((dy - SPIRE_WALL) / (double) (SPIRE_TOP - SPIRE_WALL)));
    }

    /**
     * Torre-colmeia cônica de 52 blocos com a Câmara da Matriarca (48 de diâmetro), ilha de base, quatro pontes e o
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
        for (int k = 1; k <= 18; k++) {
            double radius = Math.max(2, 31 - k * 1.65);
            q.disk(cx, cz, fy - 1 - k, radius, random.nextInt(5) == 0 ? Material.BLACKSTONE : Material.END_STONE);
        }
        q.cylinder(cx, cz, fy - 3, fy - 2, 28, Material.END_STONE_BRICKS);
        // volume livre acima do piso
        for (int dy = 0; dy <= SPIRE_TOP + 2; dy++) {
            q.disk(cx, cz, fy + dy, 28.5, Material.AIR);
        }
        // casca: tambor e cone sólidos, depois a câmara oca por dentro
        for (int dy = 0; dy <= SPIRE_TOP; dy++) {
            double outer = spireOuter(dy);
            for (int lx = -28; lx <= 28; lx++) {
                for (int lz = -28; lz <= 28; lz++) {
                    if (Math.hypot(lx, lz) <= outer) {
                        f.put(lx, fy + dy, lz, shell[random.nextInt(shell.length)]);
                    }
                }
            }
        }
        for (int dy = 0; dy <= SPIRE_WALL + SPIRE_DOME; dy++) {
            double inner = spireInner(dy);
            if (inner > 0.5) {
                q.disk(cx, cz, fy + dy, inner, Material.AIR);
            }
        }
        // anéis claros de End Stone Bricks no cone
        for (int dy = SPIRE_WALL + 1; dy <= SPIRE_TOP; dy += 6) {
            q.ring(cx, cz, fy + dy, spireOuter(dy) - 1, spireOuter(dy), Material.END_STONE_BRICKS);
        }
        q.set(cx, fy + SPIRE_TOP + 1, cz, Material.AMETHYST_BLOCK);
        q.set(cx, fy + SPIRE_TOP + 2, cz, Material.AMETHYST_CLUSTER);
        q.set(cx, fy + SPIRE_TOP + 3, cz, Material.END_ROD);

        // nervuras exteriores em forma de patas de inseto ancorando a torre
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k + Math.PI / 8;
            for (int dy = -3; dy <= SPIRE_WALL + 2; dy++) {
                double t = Math.max(0, 1 - dy / (double) SPIRE_WALL);
                double r = SPIRE_OUT + 8 * t * t;
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
            for (int dy : new int[] {9, 15, 27, 35, 43}) {
                double r = spireOuter(dy) - 0.5;
                Material glass = (k + dy) % 2 == 0 ? Material.ORANGE_STAINED_GLASS : Material.LIME_STAINED_GLASS;
                for (int w = -1; w <= 1; w++) {
                    double aa = a + w * (dy < SPIRE_WALL ? 0.05 : 0.09);
                    int px = (int) Math.round(cx + Math.cos(aa) * r);
                    int pz = (int) Math.round(cz + Math.sin(aa) * r);
                    for (int h = 0; h <= (dy < SPIRE_WALL ? 4 : 1); h++) {
                        q.set(px, fy + dy + h, pz, glass);
                        if (dy >= SPIRE_WALL) {
                            q.set((int) Math.round(cx + Math.cos(aa) * (r - 1.5)), fy + dy + h,
                                    (int) Math.round(cz + Math.sin(aa) * (r - 1.5)), Material.SHROOMLIGHT);
                        }
                    }
                }
            }
        }

        // piso: tijolos de ardósia com anéis
        q.disk(cx, cz, fy - 1, 28, Material.END_STONE_BRICKS);
        q.disk(cx, cz, fy - 1, 25, Material.DEEPSLATE_TILES);
        q.ring(cx, cz, fy - 1, 17, 18.4, Material.CRACKED_DEEPSLATE_TILES);
        q.ring(cx, cz, fy - 1, 9, 10.2, Material.CHISELED_DEEPSLATE);
        q.ring(cx, cz, fy - 1, 22.6, 23.6, Material.POLISHED_DEEPSLATE);
        // poças de ácido (rasas): manchas de concreto-pó verde no piso, longe da entrada
        for (int i = 0; i < 12; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 11 + random.nextDouble() * 9;
            int px = (int) Math.round(Math.cos(a) * r);
            int pz = (int) Math.round(Math.sin(a) * r);
            if (Math.abs(a - Math.PI / 2) < 0.4) {
                continue;
            }
            for (int lx = -3; lx <= 3; lx++) {
                for (int lz = -3; lz <= 3; lz++) {
                    if (lx * lx + lz * lz <= 7 + random.nextInt(3)) {
                        q.set(cx + px + lx, fy - 1, cz + pz + lz, Material.LIME_CONCRETE_POWDER);
                    }
                }
            }
        }
        // centro livre: sem altar nem ninho, para o chefe se mover por toda a câmara

        // costelas arqueadas de End Stone Bricks acompanhando a cúpula
        for (int k = 0; k < 16; k++) {
            if (k == 4) {
                continue; // entrada em +lz
            }
            double a = Math.PI / 8 * k;
            for (int dy = 0; dy <= SPIRE_WALL + SPIRE_DOME - 1; dy++) {
                double r = spireInner(dy) - 0.5;
                if (r < 1) {
                    break;
                }
                f.put((int) Math.round(Math.cos(a) * r), fy + dy, (int) Math.round(Math.sin(a) * r), Material.END_STONE_BRICKS);
                f.put((int) Math.round(Math.cos(a) * (r - 1)), fy + dy, (int) Math.round(Math.sin(a) * (r - 1)),
                        dy % 5 == 4 ? Material.SHROOMLIGHT : Material.END_STONE_BRICKS);
            }
        }
        // teias e casulos bioluminescentes pendurados no teto
        for (int i = 0; i < 52; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 4 + random.nextDouble() * 17;
            int px = (int) Math.round(cx + Math.cos(a) * r);
            int pz = (int) Math.round(cz + Math.sin(a) * r);
            int ceiling = fy + SPIRE_WALL + (int) Math.floor(SPIRE_DOME * Math.sqrt(Math.max(0, 1 - (r / SPIRE_IN) * (r / SPIRE_IN)))) - 1;
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
            for (int d = 27; d <= 58; d++) {
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
        f.box(-3, 22, 3, 30, fy, fy + 6, Material.AIR);
        f.box(-4, 23, 4, 29, fy + 7, fy + 7, Material.CRYING_OBSIDIAN);
        f.box(-4, 23, -4, 29, fy, fy + 6, Material.END_STONE_BRICKS);
        f.box(4, 23, 4, 29, fy, fy + 6, Material.END_STONE_BRICKS);
        f.put(-4, fy + 4, 30, Material.SHROOMLIGHT);
        f.put(4, fy + 4, 30, Material.SHROOMLIGHT);
        List<int[]> seals = new ArrayList<>();
        for (int lx = -3; lx <= 3; lx++) {
            for (int y = fy; y <= fy + 6; y++) {
                seals.add(f.world(lx, y, 24));
            }
        }
        return seals;
    }

    // ── Cidadela Soterrada (Parched Titan) ─────────────────────

    /** Centros (x, z no mundo) dos seis pilares de cobertura do Santuário da Fornalha Seca. */
    static int[][] pillarCenters(int cx, int cz, int fx, int fz) {
        int[][] out = new int[6][];
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(15 + 60 * k);
            int lx = (int) Math.round(Math.cos(a) * 9.5);
            int lz = (int) Math.round(Math.sin(a) * 9.5);
            out[k] = new int[] {cx + fx * lz + fz * lx, cz + fz * lz - fx * lx};
        }
        return out;
    }

    private static Material sandstone(Random random) {
        int r = random.nextInt(10);
        return r < 5 ? Material.SANDSTONE : r < 8 ? Material.CUT_SANDSTONE : r < 9 ? Material.SMOOTH_SANDSTONE
                : Material.CHISELED_SANDSTONE;
    }

    private static org.bukkit.block.data.BlockData dispenserFacing(Frame f, int dlx) {
        int dx = f.fz() * dlx;
        int dz = -f.fx() * dlx;
        org.bukkit.block.BlockFace face = dx > 0 ? org.bukkit.block.BlockFace.EAST : dx < 0 ? org.bukkit.block.BlockFace.WEST
                : dz > 0 ? org.bukkit.block.BlockFace.SOUTH : org.bukkit.block.BlockFace.NORTH;
        org.bukkit.block.data.Directional data = (org.bukkit.block.data.Directional) Material.DISPENSER.createBlockData();
        data.setFacing(face);
        return data;
    }

    private static void fillMix(Frame f, Random random, int lx1, int lz1, int lx2, int lz2, int y1, int y2) {
        for (int lx = Math.min(lx1, lx2); lx <= Math.max(lx1, lx2); lx++) {
            for (int lz = Math.min(lz1, lz2); lz <= Math.max(lz1, lz2); lz++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    f.put(lx, y, lz, sandstone(random));
                }
            }
        }
    }

    /**
     * Cidadela Soterrada: Santuario da Fornalha Seca (arena redonda de 34 de diametro e 16 de pe-direito, teto com
     * claraboias e seis pilares), Salao dos Guardioes (com varandas e o portao), o Atrio dos Ventos Cortantes (24 blocos de
     * corredor com areia movedica, fossos de espinhos e dispensers) e uma escadaria que sobe ate a entrada monumental na
     * superficie. A entrada fica em +lz.
     *
     * @param groundAt altura do terreno em (x, z), para as claraboias e as dunas
     * @param gateOut  recebe as posicoes do portao do salao dos guardioes
     * @return posicoes do selo da porta da arena
     */
    static List<int[]> parchedCitadel(BlockQueue q, int cx, int cz, int fy, int fx, int fz,
                                      java.util.function.IntBinaryOperator groundAt, List<int[]> gateOut) {
        Frame f = new Frame(q, cx, cz, fx, fz);
        Random random = new Random(cx * 31L + cz * 17L);
        final int stairs = 20;

        // 1) volumes solidos
        for (int dy = -2; dy <= 17; dy++) {
            for (int lx = -20; lx <= 20; lx++) {
                for (int lz = -20; lz <= 20; lz++) {
                    if (Math.hypot(lx, lz) <= 19.5) {
                        f.put(lx, fy + dy, lz, sandstone(random));
                    }
                }
            }
        }
        fillMix(f, random, -9, 23, 9, 47, fy - 1, fy + 11);
        fillMix(f, random, -6, 46, 6, 72, fy - 5, fy + 9);

        // 2) arena: interior livre, piso, claraboias
        for (int dy = 0; dy <= 15; dy++) {
            q.disk(cx, cz, fy + dy, 17, Material.AIR);
        }
        q.disk(cx, cz, fy - 1, 17, Material.SMOOTH_SANDSTONE);
        q.ring(cx, cz, fy - 1, 8, 9.2, Material.CHISELED_SANDSTONE);
        q.ring(cx, cz, fy - 1, 14, 15.2, Material.CUT_SANDSTONE);
        q.ring(cx, cz, fy - 1, 2.5, 4, Material.CUT_RED_SANDSTONE);
        q.disk(cx, cz, fy - 1, 2, Material.RED_SANDSTONE);
        for (int k = -1; k < 6; k++) {
            double a = k < 0 ? 0 : Math.toRadians(45 + 60 * k);
            double r = k < 0 ? 0 : 11;
            double hole = k < 0 ? 2.6 : 1.9;
            int hx = (int) Math.round(cx + Math.cos(a) * r);
            int hz = (int) Math.round(cz + Math.sin(a) * r);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.hypot(dx, dz) > hole) {
                        continue;
                    }
                    int top = Math.max(fy + 17, Math.min(fy + 44, groundAt.applyAsInt(hx + dx, hz + dz) + 2));
                    for (int y = fy + 16; y <= top; y++) {
                        q.set(hx + dx, y, hz + dz, Material.AIR);
                    }
                }
            }
        }
        // pilares de cobertura (quebraveis)
        for (int[] c : pillarCenters(cx, cz, fx, fz)) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 15; dy++) {
                        Material m = dy == 0 || dy == 15 ? Material.SMOOTH_SANDSTONE
                                : dy % 5 == 4 ? Material.CHISELED_SANDSTONE : Material.CUT_SANDSTONE;
                        q.set(c[0] + dx, fy + dy, c[1] + dz, m);
                    }
                }
            }
        }
        // dunas baixas nas bordas
        for (int lx = -17; lx <= 17; lx++) {
            for (int lz = -17; lz <= 17; lz++) {
                double d = Math.hypot(lx, lz);
                if (d >= 14.4 && d <= 16.8 && random.nextInt(100) < 52 && !(Math.abs(lx) <= 3 && lz > 12)) {
                    f.put(lx, fy, lz, Material.SAND);
                    if (random.nextInt(100) < 24) {
                        f.put(lx, fy + 1, lz, Material.SAND);
                    }
                }
            }
        }
        org.bukkit.block.data.BlockData lantern = Material.LANTERN.createBlockData("[hanging=true]");
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k + Math.PI / 8;
            q.set((int) Math.round(cx + Math.cos(a) * 14), fy + 15, (int) Math.round(cz + Math.sin(a) * 14), lantern);
        }

        // 3) porta da arena, corredor e portao
        f.box(-2, 16, 2, 23, fy, fy + 5, Material.AIR);
        f.box(-3, 16, 3, 23, fy + 6, fy + 6, Material.CHISELED_SANDSTONE);
        f.box(-3, 16, -3, 20, fy, fy + 5, Material.CHISELED_SANDSTONE);
        f.box(3, 16, 3, 20, fy, fy + 5, Material.CHISELED_SANDSTONE);
        f.put(-3, fy + 3, 21, Material.LANTERN);
        f.put(3, fy + 3, 21, Material.LANTERN);
        List<int[]> seals = new ArrayList<>();
        for (int lx = -2; lx <= 2; lx++) {
            for (int y = fy; y <= fy + 5; y++) {
                seals.add(f.world(lx, y, 18));
                gateOut.add(f.world(lx, y, 22));
            }
        }

        // 4) Salao dos Guardioes
        f.box(-7, 24, 7, 45, fy, fy + 9, Material.AIR);
        f.box(-7, 24, 7, 45, fy - 1, fy - 1, Material.CUT_SANDSTONE);
        f.box(-1, 24, 1, 45, fy - 1, fy - 1, Material.ORANGE_TERRACOTTA);
        for (int lz = 26; lz <= 44; lz += 6) {
            f.put(-3, fy + 9, lz, Material.LANTERN.createBlockData("[hanging=true]"));
            f.put(3, fy + 9, lz, Material.LANTERN.createBlockData("[hanging=true]"));
        }
        f.box(-7, 26, -5, 32, fy, fy + 4, Material.CUT_SANDSTONE);
        f.box(5, 34, 7, 40, fy, fy + 4, Material.CUT_SANDSTONE);
        f.box(-7, 26, -5, 32, fy + 4, fy + 4, Material.CHISELED_SANDSTONE);
        f.box(5, 34, 7, 40, fy + 4, fy + 4, Material.CHISELED_SANDSTONE);
        f.box(-2, 44, 2, 50, fy, fy + 5, Material.AIR);

        // 5) Atrio dos Ventos Cortantes
        f.box(-3, 48, 3, 71, fy, fy + 6, Material.AIR);
        f.box(-3, 48, 3, 71, fy - 1, fy - 1, Material.CUT_SANDSTONE);
        f.box(-3, 52, 0, 54, fy - 1, fy - 1, Material.SOUL_SAND);
        f.box(1, 59, 3, 61, fy - 1, fy - 1, Material.SOUL_SAND);
        f.box(-3, 67, 0, 69, fy - 1, fy - 1, Material.SOUL_SAND);
        org.bukkit.block.data.BlockData spike = org.bukkit.Bukkit.createBlockData(
                "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up]");
        for (int[] pit : new int[][] {{-3, 1, 56, 57}, {-1, 3, 63, 64}}) {
            f.box(pit[0], pit[2], pit[1], pit[3], fy - 4, fy - 1, Material.AIR);
            f.box(pit[0], pit[2], pit[1], pit[3], fy - 4, fy - 4, Material.DRIPSTONE_BLOCK);
            for (int lx = pit[0]; lx <= pit[1]; lx++) {
                for (int lz = pit[2]; lz <= pit[3]; lz++) {
                    if ((lx + lz) % 2 == 0) {
                        q.set(f.x(lx, lz), fy - 3, f.z(lx, lz), spike);
                    }
                }
            }
        }
        for (int lz : new int[] {51, 56, 62, 68}) {
            q.set(f.x(4, lz), fy + 1, f.z(4, lz), dispenserFacing(f, -1));
            q.set(f.x(-4, lz), fy + 1, f.z(-4, lz), dispenserFacing(f, 1));
        }
        for (int lz = 50; lz <= 68; lz += 6) {
            f.put(0, fy + 6, lz, Material.LANTERN.createBlockData("[hanging=true]"));
        }

        // 6) escadaria ate a superficie, com muralhas laterais
        for (int i = 0; i <= stairs; i++) {
            int floor = fy - 1 + i;
            for (int lz = 72 + 2 * i; lz <= 73 + 2 * i; lz++) {
                for (int lx = -5; lx <= 5; lx++) {
                    if (Math.abs(lx) <= 3) {
                        for (int y = fy - 4; y <= floor; y++) {
                            f.put(lx, y, lz, y == floor ? Material.CUT_SANDSTONE : Material.SANDSTONE);
                        }
                        for (int y = floor + 1; y <= fy + stairs + 12; y++) {
                            f.put(lx, y, lz, Material.AIR);
                        }
                    } else {
                        for (int y = fy - 4; y <= fy + stairs + 1; y++) {
                            f.put(lx, y, lz, (y + lz) % 7 == 0 ? Material.CHISELED_SANDSTONE : Material.CUT_SANDSTONE);
                        }
                    }
                }
            }
        }
        // entrada monumental: dois pilares gemeos, lintel e lanternas
        int top = fy + stairs;
        for (int side : new int[] {-1, 1}) {
            for (int lx = 5; lx <= 7; lx++) {
                for (int lz = 112; lz <= 114; lz++) {
                    for (int y = fy + stairs - 2; y <= top + 10; y++) {
                        f.put(side * lx, y, lz, y % 5 == 4 ? Material.CHISELED_SANDSTONE : Material.CUT_SANDSTONE);
                    }
                }
            }
        }
        f.box(-7, 112, 7, 114, top + 9, top + 11, Material.CHISELED_SANDSTONE);
        f.box(-4, 112, 4, 114, top + 8, top + 8, Material.CUT_SANDSTONE);
        f.put(-4, top + 7, 113, Material.LANTERN.createBlockData("[hanging=true]"));
        f.put(4, top + 7, 113, Material.LANTERN.createBlockData("[hanging=true]"));
        f.box(-4, 115, 4, 119, top, top, Material.CUT_SANDSTONE);
        for (int i = 0; i < 40; i++) {
            int lx = random.nextInt(21) - 10;
            int lz = 108 + random.nextInt(14);
            if (Math.abs(lx) <= 3 && lz < 118) {
                continue;
            }
            int ground = groundAt.applyAsInt(f.x(lx, lz), f.z(lx, lz));
            for (int h = 1; h <= 1 + random.nextInt(3); h++) {
                f.put(lx, ground + h, lz, Material.SAND);
            }
        }
        return seals;
    }

    // ── Gruta dos Esporos Ancestrais (Root Sovereign) ──────────

    /** Pontos (lx, lz) do caminho do Labirinto de Vinhas: segmentos retos de 3 blocos de largura. */
    static final int[][] GROVE_PATH = {{0, 44}, {0, 60}, {5, 60}, {5, 74}, {0, 74}, {0, 88}};
    /** Trechos de cipós densos (lx, lz) com raio de 2 blocos: lentidão e veneno. */
    static final int[][] GROVE_VINES = {{0, 50}, {5, 66}, {0, 82}};
    /** Armadilhas de tripwire (lx central, lz): cada uma dispara os dispensers das paredes. */
    static final int[][] GROVE_TRAPS = {{0, 56}, {5, 70}};
    /** Comprimento até a boca da gruta (onde a estrutura encontra a caverna natural). */
    static final int GROVE_MOUTH = 90;

    private static Material grove(Random random) {
        int r = random.nextInt(20);
        return r < 5 ? Material.MOSSY_COBBLESTONE : r < 9 ? Material.STONE : r < 13 ? Material.MOSS_BLOCK
                : r < 16 ? Material.CLAY : r < 18 ? Material.ROOTED_DIRT : Material.MOSSY_STONE_BRICKS;
    }

    /** Centros (x, z no mundo) dos seis pilares de raízes da Câmara dos Esporos. */
    static int[][] rootPillarCenters(int cx, int cz, int fx, int fz) {
        int[][] out = new int[6][];
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(30 + 60 * k);
            int lx = (int) Math.round(Math.cos(a) * 13);
            int lz = (int) Math.round(Math.sin(a) * 13);
            out[k] = new int[] {cx + fx * lz + fz * lx, cz + fz * lz - fx * lx};
        }
        return out;
    }

    private static void carve(Frame f, int lx1, int lz1, int lx2, int lz2, int y1, int y2) {
        f.box(lx1, lz1, lx2, lz2, y1, y2, Material.AIR);
    }

    /**
     * Gruta dos Esporos Ancestrais: Câmara dos Esporos (arena redonda de 36 de diâmetro e 18 de pé-direito, lagos rasos,
     * pilares de raízes e teto de Spore Blossoms), o Salão dos Protetores Botânicos, o Labirinto de Vinhas Espinhosas e a
     * boca que se abre numa lush cave. A entrada fica em +lz.
     *
     * @param gateOut recebe as posições do portão do salão
     * @return posições do selo da porta da câmara
     */
    static List<int[]> rootGrove(BlockQueue q, int cx, int cz, int fy, int fx, int fz, List<int[]> gateOut) {
        Frame f = new Frame(q, cx, cz, fx, fz);
        Random random = new Random(cx * 29L + cz * 13L);
        List<int[]> seals = new ArrayList<>();

        // 1) massa de rocha musgosa e argila
        for (int dy = -3; dy <= 19; dy++) {
            for (int lx = -22; lx <= 22; lx++) {
                for (int lz = -22; lz <= 22; lz++) {
                    if (Math.hypot(lx, lz) <= 21) {
                        f.put(lx, fy + dy, lz, grove(random));
                    }
                }
            }
        }
        for (int lx = -9; lx <= 9; lx++) {
            for (int lz = 19; lz <= 46; lz++) {
                for (int y = fy - 3; y <= fy + 9; y++) {
                    f.put(lx, y, lz, grove(random));
                }
            }
        }
        for (int s = 0; s + 1 < GROVE_PATH.length; s++) {
            int[] a = GROVE_PATH[s];
            int[] b = GROVE_PATH[s + 1];
            for (int lx = Math.min(a[0], b[0]) - 4; lx <= Math.max(a[0], b[0]) + 4; lx++) {
                for (int lz = Math.min(a[1], b[1]) - 1; lz <= Math.max(a[1], b[1]) + 1; lz++) {
                    for (int y = fy - 3; y <= fy + 8; y++) {
                        f.put(lx, y, lz, grove(random));
                    }
                }
            }
        }

        // 2) a câmara: interior livre, piso, lagos, pilares e teto
        for (int dy = 0; dy <= 17; dy++) {
            q.disk(cx, cz, fy + dy, 18, Material.AIR);
        }
        for (int lx = -18; lx <= 18; lx++) {
            for (int lz = -18; lz <= 18; lz++) {
                if (Math.hypot(lx, lz) <= 18) {
                    f.put(lx, fy - 1, lz, random.nextInt(4) == 0 ? Material.CLAY : random.nextInt(3) == 0 ? Material.MOSS_BLOCK
                            : Material.GRASS_BLOCK);
                    f.put(lx, fy - 2, lz, Material.CLAY);
                    if (random.nextInt(4) == 0) {
                        f.put(lx, fy, lz, Material.MOSS_CARPET);
                    } else if (random.nextInt(40) == 0) {
                        f.put(lx, fy, lz, random.nextBoolean() ? Material.FLOWERING_AZALEA : Material.AZALEA);
                    } else if (random.nextInt(30) == 0) {
                        f.put(lx, fy, lz, Material.SHORT_GRASS);
                    }
                }
            }
        }
        for (int k = 0; k < 4; k++) {                                       // quatro lagos rasos
            double a = Math.toRadians(45 + 90 * k);
            int px = (int) Math.round(Math.cos(a) * 7.5);
            int pz = (int) Math.round(Math.sin(a) * 7.5);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (Math.hypot(dx, dz) <= 3.2) {
                        f.put(px + dx, fy - 2, pz + dz, Material.CLAY);
                        f.put(px + dx, fy - 1, pz + dz, Material.WATER);
                        f.put(px + dx, fy, pz + dz, Material.AIR);
                    } else if (Math.hypot(dx, dz) <= 4.1) {
                        f.put(px + dx, fy - 1, pz + dz, Material.CLAY);
                        f.put(px + dx, fy, pz + dz, random.nextInt(3) == 0 ? Material.MOSS_CARPET : Material.AIR);
                    }
                }
            }
        }
        // pilares de raízes gigantes
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(30 + 60 * k);
            int px = (int) Math.round(Math.cos(a) * 13);
            int pz = (int) Math.round(Math.sin(a) * 13);
            for (int dy = 0; dy <= 17; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean core = dx == 0 && dz == 0;
                        boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                        Material m = core ? Material.MANGROVE_LOG : corner ? (dy % 3 == 0 ? Material.MUDDY_MANGROVE_ROOTS
                                : Material.MANGROVE_ROOTS) : Material.MUDDY_MANGROVE_ROOTS;
                        if (dy > 12 && corner) {
                            m = Material.AIR;
                        }
                        f.put(px + dx, fy + dy, pz + dz, m);
                    }
                }
            }
            for (int dx = -2; dx <= 2; dx++) {                              // raízes que se espalham pelo chão
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        if (random.nextInt(3) == 0) {
                            f.put(px + dx, fy, pz + dz, Material.MANGROVE_ROOTS);
                        }
                    }
                }
            }
        }
        // teto: musgo, Spore Blossoms bioluminescentes, luzes de cogumelo e raízes pendentes
        for (int lx = -18; lx <= 18; lx++) {
            for (int lz = -18; lz <= 18; lz++) {
                double d = Math.hypot(lx, lz);
                if (d > 17.5) {
                    continue;
                }
                f.put(lx, fy + 18, lz, Material.MOSS_BLOCK);
                int roll = random.nextInt(100);
                if (roll < 18) {
                    f.put(lx, fy + 17, lz, Material.SPORE_BLOSSOM);
                } else if (roll < 26) {
                    f.put(lx, fy + 17, lz, Material.HANGING_ROOTS);
                } else if (roll < 30) {
                    f.put(lx, fy + 17, lz, Material.CAVE_VINES_PLANT);
                    f.put(lx, fy + 16, lz, Material.CAVE_VINES);
                }
            }
        }
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(30 * k + 10);
            double r = k % 2 == 0 ? 9 : 15;
            f.put((int) Math.round(Math.cos(a) * r), fy + 18, (int) Math.round(Math.sin(a) * r), Material.SHROOMLIGHT);
        }
        f.put(0, fy + 18, 0, Material.SHROOMLIGHT);

        // 3) porta da câmara e túnel até o salão; selos em lz 19 e portão em lz 22
        carve(f, -2, 18, 2, 23, fy, fy + 4);
        f.box(-2, 18, 2, 23, fy - 1, fy - 1, Material.MOSSY_STONE_BRICKS);
        for (int lx = -2; lx <= 2; lx++) {
            for (int y = fy; y <= fy + 4; y++) {
                f.put(lx, y, 19, Material.MOSSY_STONE_BRICKS);
                seals.add(f.world(lx, y, 19));
                f.put(lx, y, 22, Material.MOSSY_STONE_BRICKS);
                gateOut.add(f.world(lx, y, 22));
            }
        }
        for (int y = fy + 1; y <= fy + 3; y += 2) {
            f.put(-3, y, 20, Material.SHROOMLIGHT);
            f.put(3, y, 20, Material.SHROOMLIGHT);
        }

        // 4) Salão dos Protetores Botânicos
        carve(f, -6, 24, 6, 42, fy, fy + 7);
        for (int lx = -6; lx <= 6; lx++) {
            for (int lz = 24; lz <= 42; lz++) {
                f.put(lx, fy - 1, lz, random.nextInt(3) == 0 ? Material.CLAY : random.nextBoolean() ? Material.MOSS_BLOCK
                        : Material.GRASS_BLOCK);
                f.put(lx, fy + 8, lz, Material.MOSS_BLOCK);
                int roll = random.nextInt(100);
                if (roll < 12) {
                    f.put(lx, fy + 7, lz, Material.SPORE_BLOSSOM);
                } else if (roll < 22) {
                    f.put(lx, fy + 7, lz, Material.HANGING_ROOTS);
                } else if (roll < 40) {
                    f.put(lx, fy, lz, Material.MOSS_CARPET);
                }
            }
        }
        for (int[] spot : new int[][] {{-3, 28}, {3, 28}, {-3, 38}, {3, 38}}) {
            for (int y = fy; y <= fy + 7; y++) {
                f.put(spot[0], y, spot[1], y % 4 == 3 ? Material.MUDDY_MANGROVE_ROOTS : Material.MANGROVE_LOG);
            }
        }
        for (int lz = 26; lz <= 40; lz += 7) {
            f.put(-5, fy + 7, lz, Material.SHROOMLIGHT);
            f.put(5, fy + 7, lz, Material.SHROOMLIGHT);
        }
        carve(f, -1, 43, 1, 45, fy, fy + 3);                                // passagem do salão para o labirinto
        f.box(-1, 43, 1, 45, fy - 1, fy - 1, Material.MOSS_BLOCK);

        // 5) Labirinto de Vinhas Espinhosas: corredor de 3 de largura em zigue-zague
        for (int s = 0; s + 1 < GROVE_PATH.length; s++) {
            int[] a = GROVE_PATH[s];
            int[] b = GROVE_PATH[s + 1];
            int x1 = Math.min(a[0], b[0]) - 1;
            int x2 = Math.max(a[0], b[0]) + 1;
            int z1 = Math.min(a[1], b[1]) - 1;
            int z2 = Math.max(a[1], b[1]) + 1;
            carve(f, x1, z1, x2, z2, fy, fy + 3);
            f.box(x1, z1, x2, z2, fy - 1, fy - 1, Material.MOSS_BLOCK);
            for (int lz = z1 + 1; lz <= z2 - 1; lz += 6) {
                f.put((x1 + x2) / 2, fy + 4, lz, Material.SHROOMLIGHT);
            }
        }
        for (int[] spot : GROVE_VINES) {                                    // cipós densos pendendo do teto
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int lx = spot[0] + dx;
                    int lz = spot[1] + dz;
                    f.put(lx, fy + 3, lz, Material.CAVE_VINES_PLANT);
                    f.put(lx, fy + 2, lz, Material.CAVE_VINES_PLANT);
                    f.put(lx, fy + 1, lz, Material.CAVE_VINES);
                    f.put(lx, fy, lz, Material.MOSS_CARPET);
                }
            }
        }
        for (int[] trap : GROVE_TRAPS) {                                    // tripwire e dispensers escondidos nas paredes
            for (int dx = -1; dx <= 1; dx++) {
                f.put(trap[0] + dx, fy, trap[1], Material.TRIPWIRE);
            }
            f.put(trap[0] - 2, fy + 1, trap[1], dispenserFacing(f, 1));
            f.put(trap[0] + 2, fy + 1, trap[1], dispenserFacing(f, -1));
            f.put(trap[0] - 2, fy, trap[1], Material.MOSSY_COBBLESTONE);
            f.put(trap[0] + 2, fy, trap[1], Material.MOSSY_COBBLESTONE);
        }
        // boca da gruta, aberta para a caverna natural
        carve(f, -1, 87, 1, GROVE_MOUTH + 2, fy, fy + 3);
        return seals;
    }

    // ── Covil Ressonante (Gloom Stalker) ───────────────────────

    /** Catalisadores de Ressonância da Câmara de Incubação (lx, lz): precisam ser destruídos para abrir o portão. */
    static final int[][] LAIR_CATALYSTS = {{-5, 32}, {5, 32}, {0, 40}};
    /** Trechos da Galeria dos Sensores (lz inicial, lz final) em que o piso é uma ponte sobre um fosso. */
    static final int[][] LAIR_BRIDGES = {{50, 57}, {61, 68}};
    /** Comprimento até a boca do covil (onde a estrutura encontra a caverna natural). */
    static final int LAIR_MOUTH = 76;
    static final int LAIR_GALLERY_FROM = 46;
    static final int LAIR_GALLERY_TO = 73;

    private static Material deep(Random random) {
        int r = random.nextInt(20);
        return r < 8 ? Material.DEEPSLATE : r < 12 ? Material.COBBLED_DEEPSLATE : r < 15 ? Material.DEEPSLATE_BRICKS
                : r < 17 ? Material.POLISHED_DEEPSLATE : r < 19 ? Material.SCULK : Material.TUFF;
    }

    /** Centros (x, z no mundo) dos seis pilares do Abismo do Coração de Alma. */
    static int[][] lairPillarCenters(int cx, int cz, int fx, int fz) {
        int[][] out = new int[6][];
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(20 + 60 * k);
            int lx = (int) Math.round(Math.cos(a) * 10.5);
            int lz = (int) Math.round(Math.sin(a) * 10.5);
            out[k] = new int[] {cx + fx * lz + fz * lx, cz + fz * lz - fx * lx};
        }
        return out;
    }

    /**
     * Covil Ressonante: Abismo do Coração de Alma (38 de diâmetro, 20 de pé-direito, pilares de Reinforced Deepslate e
     * Obsidian, plataformas nas bordas, sem nenhuma luz), a Câmara de Incubação Sculk (3 Catalisadores), a Galeria dos
     * Sensores e Alçapões (28 blocos, pontes de 3 blocos sobre fossos) e a boca que se abre numa caverna do Deep Dark.
     * A entrada fica em +lz.
     *
     * @param gateOut recebe as posições do portão da câmara de incubação
     * @return posições do selo da porta do abismo
     */
    static List<int[]> gloomLair(BlockQueue q, int cx, int cz, int fy, int fx, int fz, List<int[]> gateOut) {
        Frame f = new Frame(q, cx, cz, fx, fz);
        Random random = new Random(cx * 37L + cz * 11L);
        List<int[]> seals = new ArrayList<>();

        // 1) massa de deepslate e sculk
        for (int dy = -4; dy <= 21; dy++) {
            for (int lx = -22; lx <= 22; lx++) {
                for (int lz = -22; lz <= 22; lz++) {
                    if (Math.hypot(lx, lz) <= 21) {
                        f.put(lx, fy + dy, lz, deep(random));
                    }
                }
            }
        }
        for (int lx = -10; lx <= 10; lx++) {
            for (int lz = 19; lz <= LAIR_MOUTH; lz++) {
                for (int y = fy - 10; y <= fy + 9; y++) {
                    f.put(lx, y, lz, deep(random));
                }
            }
        }

        // 2) o abismo: interior livre, piso, pilares, plataformas
        for (int dy = 0; dy <= 19; dy++) {
            q.disk(cx, cz, fy + dy, 19, Material.AIR);
        }
        for (int lx = -19; lx <= 19; lx++) {
            for (int lz = -19; lz <= 19; lz++) {
                double d = Math.hypot(lx, lz);
                if (d <= 19) {
                    f.put(lx, fy - 1, lz, d < 4 ? Material.REINFORCED_DEEPSLATE : random.nextInt(3) == 0 ? Material.SCULK
                            : random.nextInt(3) == 0 ? Material.DEEPSLATE_TILES : Material.DEEPSLATE_BRICKS);
                    if (d < 17 && random.nextInt(14) == 0) {
                        f.put(lx, fy, lz, Material.SCULK_VEIN.createBlockData("[down=true]"));
                    }
                }
            }
        }
        for (int k = 0; k < 6; k++) {                                       // pilares grossos
            double a = Math.toRadians(20 + 60 * k);
            int px = (int) Math.round(Math.cos(a) * 10.5);
            int pz = (int) Math.round(Math.sin(a) * 10.5);
            for (int dy = 0; dy <= 19; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean core = dx == 0 && dz == 0;
                        f.put(px + dx, fy + dy, pz + dz, core ? Material.OBSIDIAN : dy % 5 == 4 ? Material.OBSIDIAN
                                : Material.REINFORCED_DEEPSLATE);
                    }
                }
            }
        }
        for (int k = 0; k < 6; k++) {                                       // plataformas elevadas nas bordas
            double a = Math.toRadians(50 + 60 * k);
            int px = (int) Math.round(Math.cos(a) * 16);
            int pz = (int) Math.round(Math.sin(a) * 16);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.hypot(px + dx, pz + dz) < 18.6) {
                        for (int y = fy; y <= fy + 3; y++) {
                            f.put(px + dx, y, pz + dz, y == fy + 3 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_BRICKS);
                        }
                    }
                }
            }
            int sx = (int) Math.round(Math.cos(a) * 12.6);
            int sz = (int) Math.round(Math.sin(a) * 12.6);
            f.put(sx, fy, sz, Material.DEEPSLATE_BRICK_SLAB);
            f.put((sx + px) / 2, fy + 1, (sz + pz) / 2, Material.DEEPSLATE_BRICK_SLAB);
        }
        for (int lx = -19; lx <= 19; lx++) {                                // teto de sculk e gotejamento de veias
            for (int lz = -19; lz <= 19; lz++) {
                if (Math.hypot(lx, lz) <= 18.5 && random.nextInt(5) == 0) {
                    f.put(lx, fy + 19, lz, random.nextBoolean() ? Material.SCULK.createBlockData() : Material.SCULK_VEIN.createBlockData("[up=true]"));
                }
            }
        }
        for (int k = 0; k < 8; k++) {                                       // sensores de sculk ao pé das paredes
            double a = Math.toRadians(45 * k + 12);
            f.put((int) Math.round(Math.cos(a) * 17.5), fy, (int) Math.round(Math.sin(a) * 17.5), Material.SCULK_SENSOR);
        }

        // 3) porta do abismo e túnel até a câmara de incubação; selos em lz 20 e portão em lz 23
        carve(f, -2, 19, 2, 24, fy, fy + 4);
        f.box(-2, 19, 2, 24, fy - 1, fy - 1, Material.DEEPSLATE_TILES);
        for (int lx = -2; lx <= 2; lx++) {
            for (int y = fy; y <= fy + 4; y++) {
                f.put(lx, y, 20, Material.REINFORCED_DEEPSLATE);
                seals.add(f.world(lx, y, 20));
                f.put(lx, y, 23, Material.REINFORCED_DEEPSLATE);
                gateOut.add(f.world(lx, y, 23));
            }
        }

        // 4) Câmara de Incubação Sculk: salão com os três Catalisadores de Ressonância
        carve(f, -8, 25, 8, 45, fy, fy + 7);
        for (int lx = -8; lx <= 8; lx++) {
            for (int lz = 25; lz <= 45; lz++) {
                f.put(lx, fy - 1, lz, random.nextInt(3) == 0 ? Material.SCULK : Material.DEEPSLATE_BRICKS);
                if (random.nextInt(9) == 0) {
                    f.put(lx, fy, lz, Material.SCULK_VEIN.createBlockData("[down=true]"));
                }
                if (random.nextInt(14) == 0) {
                    f.put(lx, fy + 7, lz, Material.SCULK_VEIN.createBlockData("[up=true]"));
                }
            }
        }
        for (int[] spot : LAIR_CATALYSTS) {
            f.put(spot[0], fy - 1, spot[1], Material.SCULK);
            f.put(spot[0], fy, spot[1], Material.SCULK_CATALYST);
        }
        for (int[] spot : new int[][] {{-4, 28}, {4, 28}, {-4, 37}, {4, 37}, {-4, 43}, {4, 43}}) {
            for (int y = fy; y <= fy + 7; y++) {
                f.put(spot[0], y, spot[1], y % 4 == 3 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES);
            }
        }
        carve(f, -2, 46, 2, 47, fy, fy + 4);                                // passagem para a galeria
        f.box(-2, 46, 2, 47, fy - 1, fy - 1, Material.DEEPSLATE_TILES);

        // 5) Galeria dos Sensores e Alçapões: túnel escuro de 28 blocos, fossos e pontes estreitas
        carve(f, -2, LAIR_GALLERY_FROM, 2, LAIR_GALLERY_TO + 2, fy, fy + 4);
        f.box(-2, LAIR_GALLERY_FROM, 2, LAIR_GALLERY_TO + 2, fy - 1, fy - 1, Material.DEEPSLATE_TILES);
        for (int lz = LAIR_GALLERY_FROM + 2; lz <= LAIR_GALLERY_TO; lz++) {
            for (int side : new int[] {-3, 3}) {
                for (int y = fy; y <= fy + 4; y++) {
                    if (random.nextInt(3) == 0) {
                        f.put(side, y, lz, Material.SCULK);
                    }
                }
                if (lz % 4 == 0) {
                    f.put(side, fy + 1, lz, Material.SCULK_SENSOR);
                }
            }
        }
        for (int[] bridge : LAIR_BRIDGES) {                                 // fosso de 7 blocos de profundidade, ponte de 3 de largura
            for (int lz = bridge[0]; lz <= bridge[1]; lz++) {
                for (int lx = -2; lx <= 2; lx++) {
                    for (int y = fy - 8; y <= fy - 1; y++) {
                        f.put(lx, y, lz, y == fy - 8 ? Material.SCULK : Material.AIR);
                    }
                    if (Math.abs(lx) <= 1) {
                        f.put(lx, fy - 1, lz, Material.DEEPSLATE_BRICKS);
                    }
                }
            }
        }
        // boca do covil, aberta para a caverna natural
        carve(f, -2, LAIR_GALLERY_TO + 1, 2, LAIR_MOUTH + 2, fy, fy + 4);
        return seals;
    }
}

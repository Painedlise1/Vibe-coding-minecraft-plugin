package com.dungeonscore.dungeon;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.dungeonscore.dungeon.CityLayout.CENTER;
import static com.dungeonscore.dungeon.CityLayout.FLOOR;
import static com.dungeonscore.dungeon.CityLayout.PLAZA_HI;
import static com.dungeonscore.dungeon.CityLayout.PLAZA_LO;
import static com.dungeonscore.dungeon.CityLayout.RADIUS;
import static com.dungeonscore.dungeon.CityLayout.noise;
import static com.dungeonscore.dungeon.CityLayout.rand;

/**
 * Generates the Overgrown City: ruined, vine-covered streets around a walled plaza with a giant tree, enclosed by
 * a city wall and a band of dense jungle, with void beyond. Every block is a pure function of its coordinates, so
 * chunks can be generated in any order and on any thread.
 */
public final class OvergrownCityGenerator extends ChunkGenerator {

    private static final int BASE = FLOOR - 8;
    private static final int HEIGHT = 48;
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    // Block palette - created once on the main thread when the class loads, then only read.
    private static final BlockData BEDROCK = d(Material.BEDROCK);
    private static final BlockData STONE = d(Material.STONE);
    private static final BlockData DIRT = d(Material.DIRT);
    private static final BlockData GRASS = d(Material.GRASS_BLOCK);
    private static final BlockData MOSS = d(Material.MOSS_BLOCK);
    private static final BlockData ROOTED = d(Material.ROOTED_DIRT);
    private static final BlockData COARSE = d(Material.COARSE_DIRT);
    private static final BlockData GRAVEL = d(Material.GRAVEL);
    private static final BlockData COBBLE = d(Material.COBBLESTONE);
    private static final BlockData MOSSY_COBBLE = d(Material.MOSSY_COBBLESTONE);
    private static final BlockData ANDESITE = d(Material.ANDESITE);
    private static final BlockData POLISHED = d(Material.POLISHED_ANDESITE);
    private static final BlockData SMOOTH = d(Material.SMOOTH_STONE);
    private static final BlockData BRICK = d(Material.STONE_BRICKS);
    private static final BlockData MOSSY_BRICK = d(Material.MOSSY_STONE_BRICKS);
    private static final BlockData CRACKED_BRICK = d(Material.CRACKED_STONE_BRICKS);
    private static final BlockData CHISELED = d(Material.CHISELED_STONE_BRICKS);
    private static final BlockData WATER = d(Material.WATER);
    private static final BlockData LILY = d(Material.LILY_PAD);
    private static final BlockData CAMPFIRE = d(Material.CAMPFIRE);
    private static final BlockData LANTERN = d(Material.LANTERN);
    private static final BlockData POST = d(Material.MOSSY_COBBLESTONE_WALL);
    private static final BlockData GATE = d(CityLayout.GATE_MATERIAL);
    private static final BlockData MOSS_CARPET = d(Material.MOSS_CARPET);
    private static final BlockData SHORT_GRASS = d(Material.SHORT_GRASS);
    private static final BlockData FERN = d(Material.FERN);
    private static final BlockData SPORE_BLOSSOM = d(Material.SPORE_BLOSSOM);
    private static final BlockData[] FLOWERS = {
            d(Material.DANDELION), d(Material.POPPY), d(Material.AZURE_BLUET),
            d(Material.OXEYE_DAISY), d(Material.CORNFLOWER), d(Material.BLUE_ORCHID)};
    private static final BlockData JUNGLE_LEAVES = leaves(Material.JUNGLE_LEAVES);
    private static final BlockData OAK_LEAVES = leaves(Material.OAK_LEAVES);
    private static final BlockData AZALEA_LEAVES = leaves(Material.AZALEA_LEAVES);
    private static final BlockData FLOWERING_LEAVES = leaves(Material.FLOWERING_AZALEA_LEAVES);
    private static final BlockData JUNGLE_LOG = log(Material.JUNGLE_LOG, Axis.Y);
    private static final BlockData JUNGLE_LOG_X = log(Material.JUNGLE_LOG, Axis.X);
    private static final BlockData JUNGLE_LOG_Z = log(Material.JUNGLE_LOG, Axis.Z);
    private static final BlockData OAK_LOG = log(Material.OAK_LOG, Axis.Y);
    private static final BlockData[] VINES = new BlockData[4];
    private static final BlockData[] LICHEN = new BlockData[4];
    private static final BlockData CAVE_BODY = caveVine(Material.CAVE_VINES_PLANT, false);
    private static final BlockData CAVE_BODY_BERRY = caveVine(Material.CAVE_VINES_PLANT, true);
    private static final BlockData CAVE_TIP = caveVine(Material.CAVE_VINES, false);
    private static final BlockData CAVE_TIP_BERRY = caveVine(Material.CAVE_VINES, true);

    private static final Set<Material> MASONRY = EnumSet.of(Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.COBBLESTONE,
            Material.MOSSY_COBBLESTONE, Material.ANDESITE, Material.POLISHED_ANDESITE, Material.SMOOTH_STONE);

    static {
        for (int i = 0; i < 4; i++) {
            MultipleFacing vine = (MultipleFacing) Material.VINE.createBlockData();
            vine.setFace(SIDES[i], true);
            VINES[i] = vine;
            MultipleFacing lichen = (MultipleFacing) Material.GLOW_LICHEN.createBlockData();
            for (BlockFace face : lichen.getFaces()) {
                lichen.setFace(face, false);
            }
            lichen.setFace(SIDES[i], true);
            LICHEN[i] = lichen;
        }
    }

    private static BlockData d(Material m) {
        return m.createBlockData();
    }

    private static BlockData leaves(Material m) {
        Leaves leaves = (Leaves) m.createBlockData();
        leaves.setPersistent(true);
        return leaves;
    }

    private static BlockData log(Material m, Axis axis) {
        Orientable log = (Orientable) m.createBlockData();
        log.setAxis(axis);
        return log;
    }

    private static BlockData caveVine(Material m, boolean berries) {
        CaveVinesPlant vine = (CaveVinesPlant) m.createBlockData();
        vine.setBerries(berries);
        return vine;
    }

    // ------------------------------------------------------------------ //
    // Generator hooks
    // ------------------------------------------------------------------ //

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData chunk) {
        int ring = Math.max(Math.abs(chunkX), Math.abs(chunkZ));
        if (ring > RADIUS + 2) {
            return;
        }
        Canvas c = new Canvas(chunkX, chunkZ);
        if (ring > RADIUS) {
            forest(c);
            c.write(chunk);
            return;
        }
        foundation(c);
        Random r = new Random(CityLayout.hash(chunkX, 0, chunkZ, 1));
        CityLayout.CellType type = CityLayout.type(chunkX, chunkZ);
        if (type == CityLayout.CellType.PLAZA) {
            plaza(c);
        } else {
            streets(c);
            switch (type) {
                case START -> start(c);
                case TOWER -> tower(c, r);
                case HOUSE -> house(c, r);
                case PARK -> park(c, r);
                case RUBBLE -> rubble(c, r);
                case FOUNTAIN -> fountain(c);
                default -> { }
            }
            lamps(c);
            boundaryWall(c);
        }
        overgrow(c);
        c.write(chunk);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new JungleBiomes();
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return CityLayout.startLocation(world);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    private static final class JungleBiomes extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return Biome.JUNGLE;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return List.of(Biome.JUNGLE);
        }
    }

    // ------------------------------------------------------------------ //
    // Canvas - a chunk-sized block buffer so features can be post-processed
    // ------------------------------------------------------------------ //

    private static final class Canvas {
        final int cx;
        final int cz;
        final BlockData[] blocks = new BlockData[16 * 16 * HEIGHT];

        Canvas(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }

        int gx(int lx) {
            return cx * 16 + lx;
        }

        int gz(int lz) {
            return cz * 16 + lz;
        }

        boolean in(int lx, int y, int lz) {
            return lx >= 0 && lx < 16 && lz >= 0 && lz < 16 && y >= BASE && y < BASE + HEIGHT;
        }

        void set(int lx, int y, int lz, BlockData data) {
            if (in(lx, y, lz)) {
                blocks[((y - BASE) * 16 + lz) * 16 + lx] = data;
            }
        }

        /** Set using world coordinates; ignored if outside this chunk. */
        void setG(int x, int y, int z, BlockData data) {
            set(x - cx * 16, y, z - cz * 16, data);
        }

        BlockData get(int lx, int y, int lz) {
            return in(lx, y, lz) ? blocks[((y - BASE) * 16 + lz) * 16 + lx] : null;
        }

        boolean air(int lx, int y, int lz) {
            return in(lx, y, lz) && get(lx, y, lz) == null;
        }

        boolean solid(int lx, int y, int lz) {
            BlockData b = get(lx, y, lz);
            return b != null && b.getMaterial().isOccluding();
        }

        void write(ChunkData chunk) {
            for (int y = BASE; y < BASE + HEIGHT; y++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        BlockData b = blocks[((y - BASE) * 16 + lz) * 16 + lx];
                        if (b != null) {
                            chunk.setBlock(lx, y, lz, b);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Palette helpers
    // ------------------------------------------------------------------ //

    private static BlockData brick(int x, int y, int z) {
        double r = rand(x, y, z, 3);
        return r < 0.45 ? BRICK : r < 0.8 ? MOSSY_BRICK : CRACKED_BRICK;
    }

    private static BlockData leafMix(int x, int y, int z) {
        double r = rand(x, y, z, 4);
        return r < 0.4 ? JUNGLE_LEAVES : r < 0.65 ? OAK_LEAVES : r < 0.85 ? AZALEA_LEAVES : FLOWERING_LEAVES;
    }

    private static BlockData roadBlock(int x, int z) {
        double n = noise(x, z, 6, 1);
        double r = rand(x, 0, z, 2);
        if (n > 0.64) {
            return r < 0.5 ? MOSS : GRASS;
        }
        if (n > 0.57) {
            return r < 0.5 ? ROOTED : COARSE;
        }
        if (r < 0.25) {
            return COBBLE;
        }
        if (r < 0.55) {
            return MOSSY_COBBLE;
        }
        if (r < 0.75) {
            return ANDESITE;
        }
        if (r < 0.85) {
            return GRAVEL;
        }
        return CRACKED_BRICK;
    }

    private static BlockData sidewalkBlock(int x, int z) {
        double r = rand(x, 0, z, 5);
        return r < 0.5 ? POLISHED : r < 0.7 ? BRICK : r < 0.9 ? MOSSY_BRICK : MOSS;
    }

    private static BlockData floorTile(int x, int z) {
        double r = rand(x, 1, z, 6);
        return r < 0.4 ? POLISHED : r < 0.65 ? SMOOTH : r < 0.85 ? MOSSY_COBBLE : MOSS;
    }

    // ------------------------------------------------------------------ //
    // Terrain
    // ------------------------------------------------------------------ //

    private static void foundation(Canvas c) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                c.set(lx, BASE, lz, BEDROCK);
                for (int y = BASE + 1; y <= FLOOR - 3; y++) {
                    c.set(lx, y, lz, STONE);
                }
                for (int y = FLOOR - 2; y <= FLOOR; y++) {
                    c.set(lx, y, lz, DIRT);
                }
            }
        }
    }

    /** Dense jungle outside the wall so the horizon isn't void. */
    private static void forest(Canvas c) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = c.gx(lx);
                int z = c.gz(lz);
                c.set(lx, FLOOR - 1, lz, DIRT);
                c.set(lx, FLOOR, lz, GRASS);
                int top = FLOOR + 3 + (int) (noise(x, z, 5, 30) * 12 + noise(x, z, 13, 31) * 8);
                boolean trunk = rand(x, 0, z, 32) < 0.03;
                for (int y = FLOOR + 1; y <= top; y++) {
                    c.set(lx, y, lz, trunk && y < top ? JUNGLE_LOG : leafMix(x, y, z));
                }
            }
        }
    }

    private static void streets(Canvas c) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = c.gx(lx);
                int z = c.gz(lz);
                boolean road = lx <= 2 || lx >= 13 || lz <= 2 || lz >= 13;
                boolean walk = !road && (lx == 3 || lx == 12 || lz == 3 || lz == 12);
                if (road) {
                    c.set(lx, FLOOR, lz, roadBlock(x, z));
                } else if (walk) {
                    c.set(lx, FLOOR, lz, sidewalkBlock(x, z));
                } else {
                    double r = rand(x, 2, z, 7);
                    c.set(lx, FLOOR, lz, r < 0.5 ? GRASS : r < 0.75 ? COARSE : r < 0.9 ? MOSS : ROOTED);
                }
            }
        }
    }

    private static void lamps(Canvas c) {
        int[][] corners = {{3, 3}, {3, 12}, {12, 3}, {12, 12}};
        for (int[] p : corners) {
            int x = c.gx(p[0]);
            int z = c.gz(p[1]);
            double r = rand(x, 3, z, 8);
            if (r > 0.75) {
                continue;
            }
            int height = r < 0.2 ? 1 : 3;
            for (int y = FLOOR + 1; y <= FLOOR + height; y++) {
                c.set(p[0], y, p[1], POST);
            }
            if (height == 3) {
                c.set(p[0], FLOOR + 4, p[1], LANTERN);
            }
        }
    }

    private static void boundaryWall(Canvas c) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                boolean wall = (c.cx == -RADIUS && lx <= 2) || (c.cx == RADIUS && lx >= 13)
                        || (c.cz == -RADIUS && lz <= 2) || (c.cz == RADIUS && lz >= 13);
                if (!wall) {
                    continue;
                }
                int x = c.gx(lx);
                int z = c.gz(lz);
                int top = FLOOR + 13 + (Math.floorMod(x + z, 4) < 2 ? 1 : 0);
                c.set(lx, FLOOR, lz, BRICK);
                for (int y = FLOOR + 1; y <= top; y++) {
                    c.set(lx, y, lz, y == FLOOR + 6 && rand(x, y, z, 9) < 0.3 ? CHISELED : brick(x, y, z));
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Lots
    // ------------------------------------------------------------------ //

    private static int side(int lx, int lz) {
        return lz == 4 ? 0 : lz == 11 ? 1 : lx == 4 ? 2 : 3;
    }

    private static void tower(Canvas c, Random r) {
        int h = 9 + r.nextInt(14);
        int door = r.nextInt(4);
        for (int lx = 4; lx <= 11; lx++) {
            for (int lz = 4; lz <= 11; lz++) {
                int x = c.gx(lx);
                int z = c.gz(lz);
                boolean ex = lx == 4 || lx == 11;
                boolean ez = lz == 4 || lz == 11;
                c.set(lx, FLOOR, lz, floorTile(x, z));
                if (ex || ez) {
                    boolean corner = ex && ez;
                    int along = ex ? lz : lx;
                    int top = FLOOR + h - (int) (rand(x, 1, z, 10) * 4) + (corner ? 2 : 0);
                    for (int y = FLOOR + 1; y <= top; y++) {
                        int rel = y - FLOOR;
                        if (!corner) {
                            double t = rel / (double) h;
                            if (rand(x, y, z, 11) < 0.03 + 0.4 * t * t) {
                                continue;
                            }
                            if (rel <= 3 && along >= 7 && along <= 8 && side(lx, lz) == door) {
                                continue;
                            }
                            if ((rel % 5 == 2 || rel % 5 == 3) && (along == 6 || along == 9)) {
                                continue;
                            }
                        }
                        c.set(lx, y, lz, corner ? (rand(x, y, z, 12) < 0.7 ? BRICK : MOSSY_BRICK) : brick(x, y, z));
                    }
                } else {
                    for (int y = FLOOR + 5; y < FLOOR + h - 2; y += 5) {
                        if (rand(x, y, z, 13) > 0.3) {
                            c.set(lx, y, lz, brick(x, y, z));
                        }
                    }
                    if (rand(x, 0, z, 14) < 0.08) {
                        c.set(lx, FLOOR + 1, lz, MOSSY_COBBLE);
                    }
                }
            }
        }
    }

    private static void house(Canvas c, Random r) {
        int h = 4 + r.nextInt(3);
        int door = r.nextInt(4);
        for (int lx = 4; lx <= 11; lx++) {
            for (int lz = 4; lz <= 11; lz++) {
                int x = c.gx(lx);
                int z = c.gz(lz);
                boolean ex = lx == 4 || lx == 11;
                boolean ez = lz == 4 || lz == 11;
                c.set(lx, FLOOR, lz, floorTile(x, z));
                if (ex || ez) {
                    boolean corner = ex && ez;
                    int along = ex ? lz : lx;
                    int top = FLOOR + h - (int) (rand(x, 1, z, 15) * 3);
                    for (int y = FLOOR + 1; y <= top; y++) {
                        int rel = y - FLOOR;
                        if (!corner && rand(x, y, z, 16) < 0.22) {
                            continue;
                        }
                        if (rel <= 3 && along >= 7 && along <= 8 && side(lx, lz) == door) {
                            continue;
                        }
                        if (!corner && rel == 2 && (along == 6 || along == 9)) {
                            continue;
                        }
                        c.set(lx, y, lz, brick(x, y, z));
                    }
                } else {
                    if (rand(x, 0, z, 17) < 0.45) {
                        c.set(lx, FLOOR + h, lz, brick(x, FLOOR + h, z));
                    }
                    double d = rand(x, 0, z, 18);
                    if (d < 0.12) {
                        c.set(lx, FLOOR + 1, lz, MOSSY_COBBLE);
                    } else if (d < 0.18) {
                        c.set(lx, FLOOR + 1, lz, AZALEA_LEAVES);
                    }
                }
            }
        }
    }

    private static void park(Canvas c, Random r) {
        for (int lx = 4; lx <= 11; lx++) {
            for (int lz = 4; lz <= 11; lz++) {
                c.set(lx, FLOOR, lz, rand(c.gx(lx), 3, c.gz(lz), 19) < 0.15 ? MOSS : GRASS);
            }
        }
        boolean pond = r.nextDouble() < 0.5;
        int px = r.nextBoolean() ? 4 : 9;
        int pz = r.nextBoolean() ? 4 : 9;
        if (pond) {
            for (int lx = px; lx < px + 3; lx++) {
                for (int lz = pz; lz < pz + 3; lz++) {
                    c.set(lx, FLOOR, lz, WATER);
                    if (rand(c.gx(lx), 4, c.gz(lz), 20) < 0.3) {
                        c.set(lx, FLOOR + 1, lz, LILY);
                    }
                }
            }
        }
        int tx = 7 + r.nextInt(2);
        int tz = 7 + r.nextInt(2);
        int th = 5 + r.nextInt(3);
        boolean jungle = r.nextBoolean();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    double dist = dx * dx + dy * dy * 1.6 + dz * dz;
                    int x = c.gx(tx + dx);
                    int y = FLOOR + th + dy;
                    int z = c.gz(tz + dz);
                    if (dist <= 10.5 && rand(x, y, z, 21) < 0.92) {
                        c.set(tx + dx, y, tz + dz, rand(x, y, z, 22) < 0.2 ? FLOWERING_LEAVES
                                : jungle ? JUNGLE_LEAVES : OAK_LEAVES);
                    }
                }
            }
        }
        for (int y = FLOOR + 1; y <= FLOOR + th; y++) {
            c.set(tx, y, tz, jungle ? JUNGLE_LOG : OAK_LOG);
        }
        for (int i = 0; i < 3; i++) {
            int bx = 4 + r.nextInt(8);
            int bz = 4 + r.nextInt(8);
            boolean inPond = pond && bx >= px && bx < px + 3 && bz >= pz && bz < pz + 3;
            if (!inPond && !(bx == tx && bz == tz)) {
                c.set(bx, FLOOR + 1, bz, i == 0 ? FLOWERING_LEAVES : AZALEA_LEAVES);
            }
        }
    }

    private static void rubble(Canvas c, Random r) {
        for (int lx = 4; lx <= 11; lx++) {
            for (int lz = 4; lz <= 11; lz++) {
                int x = c.gx(lx);
                int z = c.gz(lz);
                double g = rand(x, 5, z, 23);
                c.set(lx, FLOOR, lz, g < 0.4 ? GRAVEL : g < 0.7 ? COARSE : MOSSY_COBBLE);
                int pile = (int) Math.floor(noise(x, z, 4, 24) * 4.2 - 1.2);
                for (int y = FLOOR + 1; y <= FLOOR + pile; y++) {
                    double b = rand(x, y, z, 25);
                    c.set(lx, y, lz, b < 0.4 ? MOSSY_COBBLE : b < 0.6 ? COBBLE : b < 0.8 ? CRACKED_BRICK : ANDESITE);
                }
            }
        }
        int line = 6 + r.nextInt(4);
        boolean alongX = r.nextBoolean();
        for (int i = 5; i <= 10; i++) {
            if (alongX) {
                c.set(i, FLOOR + 1, line, BRICK);
            } else {
                c.set(line, FLOOR + 1, i, BRICK);
            }
        }
        for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
            if (alongX) {
                c.set(4, y, line, MOSSY_BRICK);
            } else {
                c.set(line, y, 4, MOSSY_BRICK);
            }
        }
    }

    private static void fountain(Canvas c) {
        for (int lx = 4; lx <= 11; lx++) {
            for (int lz = 4; lz <= 11; lz++) {
                int x = c.gx(lx);
                int z = c.gz(lz);
                c.set(lx, FLOOR, lz, (lx + lz) % 2 == 0 ? POLISHED : brick(x, FLOOR, z));
                if (lx >= 5 && lx <= 10 && lz >= 5 && lz <= 10) {
                    boolean rim = lx == 5 || lx == 10 || lz == 5 || lz == 10;
                    if (rim) {
                        c.set(lx, FLOOR + 1, lz, brick(x, FLOOR + 1, z));
                    } else {
                        c.set(lx, FLOOR + 1, lz, WATER);
                        if (rand(x, 6, z, 26) < 0.15) {
                            c.set(lx, FLOOR + 2, lz, LILY);
                        }
                    }
                }
            }
        }
        for (int lx = 7; lx <= 8; lx++) {
            for (int lz = 7; lz <= 8; lz++) {
                for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                    c.set(lx, y, lz, y == FLOOR + 4 ? CHISELED : MOSSY_BRICK);
                }
                c.set(lx, FLOOR + 5, lz, LANTERN);
            }
        }
    }

    private static void start(Canvas c) {
        for (int lx = 4; lx <= 11; lx++) {
            for (int lz = 4; lz <= 11; lz++) {
                c.set(lx, FLOOR, lz, (lx + lz) % 2 == 0 ? POLISHED : SMOOTH);
            }
        }
        c.set(7, FLOOR + 1, 7, CAMPFIRE);
        int[][] bushes = {{4, 4}, {11, 4}, {4, 11}, {11, 11}};
        for (int[] b : bushes) {
            c.set(b[0], FLOOR + 1, b[1], FLOWERING_LEAVES);
        }
    }

    // ------------------------------------------------------------------ //
    // Heart of the Grove plaza (cells -1..1)
    // ------------------------------------------------------------------ //

    private static void plaza(Canvas c) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = c.gx(lx);
                int z = c.gz(lz);
                if (x < PLAZA_LO || x > PLAZA_HI || z < PLAZA_LO || z > PLAZA_HI) {
                    c.set(lx, FLOOR, lz, roadBlock(x, z));
                    continue;
                }
                boolean wallX = x <= PLAZA_LO + 1 || x >= PLAZA_HI - 1;
                boolean wallZ = z <= PLAZA_LO + 1 || z >= PLAZA_HI - 1;
                if (wallX || wallZ) {
                    c.set(lx, FLOOR, lz, BRICK);
                    int along = wallX ? z : x;
                    boolean pillar = Math.floorMod(along - PLAZA_LO, 6) <= 1 || (wallX && wallZ);
                    int top = FLOOR + (pillar ? 9 : 6);
                    for (int y = FLOOR + 1; y <= top; y++) {
                        if (CityLayout.isGate(x, y, z)) {
                            c.set(lx, y, lz, GATE);
                            continue;
                        }
                        if (!pillar && y >= FLOOR + 6 && rand(x, y, z, 50) < 0.25) {
                            continue;
                        }
                        c.set(lx, y, lz, pillar && y == top ? CHISELED : brick(x, y, z));
                    }
                    continue;
                }
                double dx = x + 0.5 - CENTER;
                double dz = z + 0.5 - CENTER;
                double dist = Math.sqrt(dx * dx + dz * dz);
                double r = rand(x, 7, z, 51);
                BlockData ground;
                if (dist > 9.5 && dist < 12.5) {
                    ground = r < 0.6 ? POLISHED : r < 0.85 ? MOSSY_BRICK : MOSS;
                } else if (dist >= 12.5 && (Math.abs(dx) < 2.5 || Math.abs(dz) < 2.5)) {
                    ground = r < 0.5 ? POLISHED : r < 0.8 ? BRICK : MOSS;
                } else {
                    ground = noise(x, z, 5, 52) > 0.5 ? MOSS : GRASS;
                }
                c.set(lx, FLOOR, lz, ground);
            }
        }
        plazaPillars(c);
        giantTree(c);
    }

    private static void plazaPillars(Canvas c) {
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 8 + k * Math.PI / 4;
            int px = (int) Math.floor(CENTER + 15 * Math.cos(a));
            int pz = (int) Math.floor(CENTER + 15 * Math.sin(a));
            int top = FLOOR + 2 + (int) (rand(px, 0, pz, 53) * 6);
            for (int ox = 0; ox <= 1; ox++) {
                for (int oz = 0; oz <= 1; oz++) {
                    for (int y = FLOOR + 1; y <= top - (ox + oz == 2 ? 1 : 0); y++) {
                        c.setG(px + ox, y, pz + oz, brick(px + ox, y, pz + oz));
                    }
                }
            }
            if (rand(px, 1, pz, 54) < 0.6) {
                c.setG(px, top + 1, pz, LANTERN);
            }
        }
    }

    private static void giantTree(Canvas c) {
        int trunkTop = FLOOR + 16;
        int canopyMid = FLOOR + 18;
        // canopy
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = c.gx(lx);
                int z = c.gz(lz);
                double dx = x + 0.5 - CENTER;
                double dz = z + 0.5 - CENTER;
                double h2 = (dx * dx + dz * dz) / (10.5 * 10.5);
                if (h2 > 1.2) {
                    continue;
                }
                for (int y = canopyMid - 6; y <= canopyMid + 5; y++) {
                    double dy = (y - canopyMid) / 4.5;
                    double v = h2 + dy * dy;
                    if (v <= 1 + (rand(x, y, z, 55) - 0.5) * 0.3 && rand(x, y, z, 56) < 0.93) {
                        double p = rand(x, y, z, 57);
                        c.set(lx, y, lz, p < 0.5 ? JUNGLE_LEAVES : p < 0.75 ? OAK_LEAVES : FLOWERING_LEAVES);
                    }
                }
                // glow berry vines hanging from the canopy
                if (rand(x, 0, z, 58) < 0.10) {
                    int low = -1;
                    for (int y = canopyMid - 6; y <= canopyMid + 5; y++) {
                        if (c.get(lx, y, lz) != null) {
                            low = y;
                            break;
                        }
                    }
                    if (low > 0) {
                        int len = 2 + (int) (rand(x, 1, z, 59) * 6);
                        for (int i = 1; i <= len && low - i > FLOOR + 4; i++) {
                            boolean berries = rand(x, low - i, z, 60) < 0.4;
                            boolean tip = i == len || low - i - 1 <= FLOOR + 4;
                            c.set(lx, low - i, lz, tip ? (berries ? CAVE_TIP_BERRY : CAVE_TIP)
                                    : (berries ? CAVE_BODY_BERRY : CAVE_BODY));
                        }
                    }
                }
            }
        }
        // trunk (2x2, centred on CENTER)
        for (int x = 7; x <= 8; x++) {
            for (int z = 7; z <= 8; z++) {
                for (int y = FLOOR + 1; y <= trunkTop; y++) {
                    c.setG(x, y, z, JUNGLE_LOG);
                }
                c.setG(x, FLOOR, z, ROOTED);
            }
        }
        // roots and branches
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int k = 0; k < dirs.length; k++) {
            int dx = dirs[k][0];
            int dz = dirs[k][1];
            int ox = dx < 0 ? 7 : 8;
            int oz = dz < 0 ? 7 : 8;
            int len = 3 + (int) (rand(k, 0, 0, 61) * 3);
            BlockData rootLog = dx != 0 && dz == 0 ? JUNGLE_LOG_X : dz != 0 && dx == 0 ? JUNGLE_LOG_Z : JUNGLE_LOG;
            for (int i = 1; i <= len; i++) {
                int x = ox + dx * i;
                int z = oz + dz * i;
                if (dx == 0) {
                    x = 7 + (k % 2);
                }
                if (dz == 0) {
                    z = 7 + (k % 2);
                }
                c.setG(x, FLOOR, z, ROOTED);
                if (i < len) {
                    c.setG(x, FLOOR + 1, z, rootLog);
                }
                if (i == 1) {
                    c.setG(x, FLOOR + 2, z, JUNGLE_LOG);
                }
            }
            if (dx != 0 && dz != 0) {
                for (int i = 1; i <= 6; i++) {
                    c.setG(ox + dx * i, FLOOR + 10 + i, oz + dz * i, JUNGLE_LOG);
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Overgrowth pass
    // ------------------------------------------------------------------ //

    private static void overgrow(Canvas c) {
        int top = BASE + HEIGHT - 2;
        // plants on soil, moss and leaf tufts on exposed masonry
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = c.gx(lx);
                int z = c.gz(lz);
                for (int y = FLOOR; y < top; y++) {
                    BlockData b = c.get(lx, y, lz);
                    if (b == null || c.get(lx, y + 1, lz) != null) {
                        continue;
                    }
                    Material m = b.getMaterial();
                    if (y == FLOOR && (m == Material.GRASS_BLOCK || m == Material.MOSS_BLOCK)) {
                        double r = rand(x, 0, z, 75);
                        if (r < 0.25) {
                            c.set(lx, y + 1, lz, SHORT_GRASS);
                        } else if (r < 0.33) {
                            c.set(lx, y + 1, lz, FERN);
                        } else if (r < 0.40) {
                            c.set(lx, y + 1, lz, FLOWERS[(int) (rand(x, 1, z, 76) * FLOWERS.length)]);
                        } else if (r < 0.47) {
                            c.set(lx, y + 1, lz, MOSS_CARPET);
                        }
                    } else if (y > FLOOR + 1 && MASONRY.contains(m)) {
                        double r = rand(x, y, z, 70);
                        if (r < 0.30) {
                            c.set(lx, y + 1, lz, MOSS_CARPET);
                        } else if (r < 0.42) {
                            c.set(lx, y + 1, lz, leafMix(x, y + 1, z));
                            if (r < 0.36) {
                                c.set(lx, y + 2, lz, leafMix(x, y + 2, z));
                            }
                        }
                    }
                }
            }
        }
        // vines trailing down walls
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = c.gx(lx);
                int z = c.gz(lz);
                for (int s = 0; s < 4; s++) {
                    int nx = lx + SIDES[s].getModX();
                    int nz = lz + SIDES[s].getModZ();
                    if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || rand(x, s, z, 71) > 0.22) {
                        continue;
                    }
                    int y = top;
                    while (y > FLOOR + 1 && !(c.air(lx, y, lz) && c.solid(nx, y, nz))) {
                        y--;
                    }
                    if (y <= FLOOR + 1) {
                        continue;
                    }
                    int len = 2 + (int) (rand(x, s, z, 72) * 10);
                    for (int i = 0; i < len && y > FLOOR && c.air(lx, y, lz); i++, y--) {
                        c.set(lx, y, lz, VINES[s]);
                    }
                }
            }
        }
        // glow lichen low on walls, glow berries and spore blossoms under ceilings
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = c.gx(lx);
                int z = c.gz(lz);
                for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                    if (!c.air(lx, y, lz)) {
                        continue;
                    }
                    for (int s = 0; s < 4; s++) {
                        if (c.solid(lx + SIDES[s].getModX(), y, lz + SIDES[s].getModZ())
                                && rand(x, y * 4 + s, z, 73) < 0.05) {
                            c.set(lx, y, lz, LICHEN[s]);
                            break;
                        }
                    }
                }
                for (int y = FLOOR + 3; y < top; y++) {
                    if (!c.air(lx, y, lz)) {
                        continue;
                    }
                    BlockData above = c.get(lx, y + 1, lz);
                    if (above == null) {
                        continue;
                    }
                    boolean sturdy = above.getMaterial().isOccluding();
                    boolean leafy = above instanceof Leaves;
                    if (!sturdy && !leafy) {
                        continue;
                    }
                    double r = rand(x, y, z, 74);
                    if (r < 0.025) {
                        int len = 1 + (int) (rand(x, y, z, 77) * 3);
                        for (int i = 0; i < len && c.air(lx, y - i, lz) && y - i > FLOOR + 2; i++) {
                            boolean berries = rand(x, y - i, z, 78) < 0.35;
                            boolean tip = i == len - 1 || !c.air(lx, y - i - 1, lz) || y - i - 1 <= FLOOR + 2;
                            c.set(lx, y - i, lz, tip ? (berries ? CAVE_TIP_BERRY : CAVE_TIP)
                                    : (berries ? CAVE_BODY_BERRY : CAVE_BODY));
                        }
                    } else if (r < 0.032 && sturdy) {
                        c.set(lx, y, lz, SPORE_BLOSSOM);
                    }
                }
            }
        }
    }
}

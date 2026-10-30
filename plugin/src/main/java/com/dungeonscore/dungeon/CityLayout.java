package com.dungeonscore.dungeon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, deterministic description of the Overgrown City layout. Shared by the chunk generator (which may run
 * off the main thread) and the dungeon manager, so everything here must be stateless.
 *
 * <p>The city is a grid of 16x16 cells, one per chunk, from -RADIUS to +RADIUS on both axes. Each cell has a
 * 6-wide road on its border (3 blocks per side), a 1-block sidewalk ring and an 8x8 building lot in the middle.
 * The 3x3 cells in the middle form the walled "Heart of the Grove" plaza, the south-centre cell is the
 * entrance courtyard and a tall wall runs around the outside.</p>
 */
public final class CityLayout {

    public static final int RADIUS = 4;
    /** Y of the street surface block; players stand at FLOOR + 1. */
    public static final int FLOOR = 64;

    /** Walkable area inside the outer city wall (block coordinates, inclusive). */
    public static final int INNER_MIN = -RADIUS * 16 + 3;
    public static final int INNER_MAX = (RADIUS + 1) * 16 - 4;

    /** Plaza wall bounds (block coordinates, inclusive). The wall itself is 2 thick. */
    public static final int PLAZA_LO = -13;
    public static final int PLAZA_HI = 28;
    /** Gate openings span these coordinates on the axis parallel to the wall. */
    public static final int GATE_LO = 6;
    public static final int GATE_HI = 9;
    public static final int GATE_TOP = FLOOR + 5;
    public static final Material GATE_MATERIAL = Material.MANGROVE_ROOTS;
    /** World coordinate of the plaza centre (the giant tree's trunk) on both X and Z. */
    public static final double CENTER = 8.0;

    private static final long SEED = 0x5EEDC17EL;

    public enum CellType { PLAZA, START, TOWER, HOUSE, PARK, RUBBLE, FOUNTAIN }

    private CityLayout() {
    }

    // ------------------------------------------------------------------ //
    // Cells
    // ------------------------------------------------------------------ //

    public static boolean inCity(int cx, int cz) {
        return Math.abs(cx) <= RADIUS && Math.abs(cz) <= RADIUS;
    }

    public static CellType type(int cx, int cz) {
        if (Math.abs(cx) <= 1 && Math.abs(cz) <= 1) {
            return CellType.PLAZA;
        }
        if (cx == 0 && cz == RADIUS) {
            return CellType.START;
        }
        double r = rand(cx, 7, cz, 99);
        if (r < 0.36) {
            return CellType.TOWER;
        }
        if (r < 0.60) {
            return CellType.HOUSE;
        }
        if (r < 0.78) {
            return CellType.PARK;
        }
        if (r < 0.93) {
            return CellType.RUBBLE;
        }
        return CellType.FOUNTAIN;
    }

    /** Whether walking into this cell triggers a mob encounter. The cells around the entrance are safe. */
    public static boolean infested(int cx, int cz) {
        if (!inCity(cx, cz)) {
            return false;
        }
        CellType type = type(cx, cz);
        if (type == CellType.PLAZA || type == CellType.START) {
            return false;
        }
        if (Math.abs(cx) <= 1 && cz >= RADIUS - 1) {
            return false;
        }
        return rand(cx, 8, cz, 100) < 0.8;
    }

    /** Elite encounters always include a Rootbound Brute. */
    public static boolean elite(int cx, int cz) {
        return rand(cx, 9, cz, 101) < 0.2;
    }

    public static int infestedCount() {
        int n = 0;
        for (int cx = -RADIUS; cx <= RADIUS; cx++) {
            for (int cz = -RADIUS; cz <= RADIUS; cz++) {
                if (infested(cx, cz)) {
                    n++;
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ //
    // Plaza and gates
    // ------------------------------------------------------------------ //

    private static boolean onPlazaWallLine(int v) {
        return v == PLAZA_LO || v == PLAZA_LO + 1 || v == PLAZA_HI - 1 || v == PLAZA_HI;
    }

    private static boolean inGateSpan(int v) {
        return v >= GATE_LO && v <= GATE_HI;
    }

    public static boolean isGate(int x, int y, int z) {
        if (y <= FLOOR || y > GATE_TOP) {
            return false;
        }
        boolean xLine = onPlazaWallLine(x) && z >= PLAZA_LO && z <= PLAZA_HI;
        boolean zLine = onPlazaWallLine(z) && x >= PLAZA_LO && x <= PLAZA_HI;
        return (xLine && inGateSpan(z)) || (zLine && inGateSpan(x));
    }

    /** Every gate block as {x, y, z}. */
    public static List<int[]> gateBlocks() {
        List<int[]> list = new ArrayList<>();
        int[] lines = {PLAZA_LO, PLAZA_LO + 1, PLAZA_HI - 1, PLAZA_HI};
        for (int y = FLOOR + 1; y <= GATE_TOP; y++) {
            for (int t = GATE_LO; t <= GATE_HI; t++) {
                for (int w : lines) {
                    list.add(new int[]{w, y, t});
                    list.add(new int[]{t, y, w});
                }
            }
        }
        return list;
    }

    /** Centre points of the four gates (for effects). */
    public static List<Location> gateCentres(World world) {
        double mid = (GATE_LO + GATE_HI + 1) / 2.0;
        double y = FLOOR + 3;
        return List.of(
                new Location(world, mid, y, PLAZA_LO + 1),
                new Location(world, mid, y, PLAZA_HI),
                new Location(world, PLAZA_LO + 1, y, mid),
                new Location(world, PLAZA_HI, y, mid));
    }

    /** Inside the plaza wall (not on it). */
    public static boolean inPlazaInterior(double x, double z) {
        return x >= PLAZA_LO + 2 && x < PLAZA_HI - 1 && z >= PLAZA_LO + 2 && z < PLAZA_HI - 1;
    }

    // ------------------------------------------------------------------ //
    // Key locations
    // ------------------------------------------------------------------ //

    public static Location startLocation(World world) {
        return new Location(world, 7.5, FLOOR + 1, RADIUS * 16 + 11.5, 180f, 0f);
    }

    public static Location bossSpawn(World world) {
        return new Location(world, CENTER, FLOOR + 1, CENTER + 13, 180f, 0f);
    }

    public static Location plazaCentre(World world) {
        return new Location(world, CENTER, FLOOR + 1, CENTER);
    }

    /** Whether a position is inside the walkable city (used to rescue players who escape over the wall). */
    public static boolean inBounds(double x, double y, double z) {
        return x >= INNER_MIN && x < INNER_MAX + 1 && z >= INNER_MIN && z < INNER_MAX + 1 && y >= FLOOR - 4;
    }

    // ------------------------------------------------------------------ //
    // Deterministic randomness
    // ------------------------------------------------------------------ //

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    public static long hash(int a, int b, int c, int salt) {
        return mix(a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + c * 0x165667B19E3779F9L
                + salt * 0xD6E8FEB86659FD93L + SEED);
    }

    /** Uniform value in [0, 1). */
    public static double rand(int a, int b, int c, int salt) {
        return (hash(a, b, c, salt) >>> 11) * 0x1.0p-53;
    }

    /** Smooth 2D value noise in [0, 1). */
    public static double noise(int x, int z, int scale, int salt) {
        int x0 = Math.floorDiv(x, scale);
        int z0 = Math.floorDiv(z, scale);
        double fx = smooth((x - x0 * (double) scale) / scale);
        double fz = smooth((z - z0 * (double) scale) / scale);
        double a = rand(x0, 0, z0, salt);
        double b = rand(x0 + 1, 0, z0, salt);
        double c = rand(x0, 0, z0 + 1, salt);
        double d = rand(x0 + 1, 0, z0 + 1, salt);
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}

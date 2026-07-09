package com.xton.fusion.wearable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.xton.fusion.wearable.LightPlacement.Cell;

/**
 * Pure-geometry coverage for {@link LightPlacement} — no Bukkit dependency, so
 * this exercises the actual placement rule directly instead of through a mock
 * world. Uses a fixed-seed {@link Random} to generate many synthetic
 * "structure" layouts (property-based in spirit: the same invariants must hold
 * for every layout, not just a couple of hand-picked ones), while staying
 * fully deterministic and reproducible run to run.
 */
class LightPlacementTest {

    private static final double MAX_DISTANCE = 1.5;
    private static final double MIN_DISTANCE = 0.3;
    private static final double STABILITY_BONUS = 0.4;
    private static final long SEED = 20260708L;
    private static final int TRIALS = 500;

    @Test
    void picksStraightAheadWhenClear() {
        // A non-integer eye position, so nothing lands exactly on a cell
        // boundary except the intentional one: idealZ (2.0) sits exactly
        // between z=1 and z=2, which is the actual case this asserts —
        // "reach out to the full configured distance" wins that tie.
        Cell chosen = LightPlacement.choose(
                0.5, 100.6, 0.5, 0, 0, 1,
                MAX_DISTANCE, MIN_DISTANCE, null, STABILITY_BONUS, cell -> true);

        assertNotNull(chosen);
        assertEquals(0, chosen.x());
        assertEquals(100, chosen.y());
        assertEquals(2, chosen.z(), "on a tie, reach out to the full configured distance");
    }

    @Test
    void nullWhenFullyEmbedded() {
        Cell chosen = LightPlacement.choose(
                0.5, 100.6, 0.5, 0, 0, 1,
                MAX_DISTANCE, MIN_DISTANCE, null, STABILITY_BONUS, cell -> false);

        assertNull(chosen);
    }

    @Test
    void findsACornerOpeningNoStraightLineThroughTheEyesCanReach() {
        // Every in-range cell is solid except (1,100,0), off to the side — dead
        // ahead, the eyes' own cell, and straight behind are all walled off. A
        // 1D ray search can never reach that opening; the sphere must.
        Cell opening = new Cell(1, 100, 0);
        Set<Cell> allInRange = new HashSet<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    allInRange.add(new Cell(dx, 100 + dy, dz));
                }
            }
        }
        allInRange.remove(opening);
        Set<Cell> solid = allInRange;

        Cell chosen = LightPlacement.choose(
                0.5, 100.5, 0.5, 0, 0, 1,
                MAX_DISTANCE, MIN_DISTANCE, null, STABILITY_BONUS,
                cell -> !solid.contains(cell));

        assertEquals(opening, chosen);
    }

    @Test
    void stabilityBonusKeepsTheIncumbentOnATrueTie() {
        // Two cells equidistant from the ideal point; without a tiebreaker the
        // scan order alone would decide. The stability bonus must make the
        // previously-lit one win regardless of which the scan would otherwise favour.
        Cell a = new Cell(1, 100, 0);
        Cell b = new Cell(-1, 100, 0);
        Cell chosen = LightPlacement.choose(
                0.5, 100.5, 0.5, 0, 0, 0, // no preferred direction: ideal point is the eye itself
                1.5, 0.0, b, STABILITY_BONUS,
                cell -> cell.equals(a) || cell.equals(b));

        assertEquals(b, chosen, "the incumbent should win a genuine tie, not whichever the scan reaches first");
    }

    @Test
    void aClearlyBetterCellStillWinsOverTheIncumbent() {
        // The incumbent is far worse than another open cell — the stability
        // bonus (0.4) must not be enough to keep a bad placement "stuck".
        Cell incumbent = new Cell(-1, 100, -1); // far from the ideal forward point
        Cell better = new Cell(0, 100, 1); // right at the ideal point
        Cell chosen = LightPlacement.choose(
                0.5, 100.5, 0.5, 0, 0, 1,
                MAX_DISTANCE, MIN_DISTANCE, incumbent, STABILITY_BONUS,
                cell -> cell.equals(incumbent) || cell.equals(better));

        assertEquals(better, chosen);
    }

    @Test
    void neverExcludesTheMinDistanceCellsFromTheFace() {
        // Every cell within MIN_DISTANCE of the eye (the "inside the face" zone)
        // must never be chosen, even if it's the only thing marked open.
        Cell chosen = LightPlacement.choose(
                0.5, 100.5, 0.5, 0, 0, 1,
                MAX_DISTANCE, MIN_DISTANCE, null, STABILITY_BONUS,
                cell -> cell.equals(new Cell(0, 100, 0)));

        assertNull(chosen, "the eyes' own cell is inside the face, not a valid placement");
    }

    /**
     * The core invariant: across many random synthetic layouts, whenever at
     * least one in-range cell is open, {@code choose} must return a non-null
     * cell that is itself genuinely open (never a solid one) — "so long as I'm
     * not embedded in something, I should have light." Repeated with the same
     * fixed seed every run, so a failure is always reproducible.
     */
    @Test
    void alwaysFindsAnOpenCellWhenOneExistsInRange() {
        Random rng = new Random(SEED);
        for (int trial = 0; trial < TRIALS; trial++) {
            double eyeX = rng.nextDouble() * 4 - 2;
            double eyeY = 100 + rng.nextDouble() * 4 - 2;
            double eyeZ = rng.nextDouble() * 4 - 2;
            double[] dir = randomUnitVector(rng);

            // A random sparse layout: each candidate cell in range is solid with
            // probability p, so most trials land somewhere between "wide open"
            // and "nearly embedded".
            double solidProbability = rng.nextDouble();
            Set<Cell> solid = new HashSet<>();
            int r = (int) Math.ceil(MAX_DISTANCE) + 1;
            int ex = (int) Math.floor(eyeX);
            int ey = (int) Math.floor(eyeY);
            int ez = (int) Math.floor(eyeZ);
            boolean anyOpenInRange = false;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        Cell cell = new Cell(ex + dx, ey + dy, ez + dz);
                        boolean isSolid = rng.nextDouble() < solidProbability;
                        if (isSolid) {
                            solid.add(cell);
                        } else if (inRange(cell, eyeX, eyeY, eyeZ)) {
                            anyOpenInRange = true;
                        }
                    }
                }
            }

            Cell chosen = LightPlacement.choose(
                    eyeX, eyeY, eyeZ, dir[0], dir[1], dir[2],
                    MAX_DISTANCE, MIN_DISTANCE, null, STABILITY_BONUS,
                    cell -> !solid.contains(cell));

            if (anyOpenInRange) {
                assertNotNull(chosen, "trial " + trial + ": an open cell exists in range but none was chosen");
                assertFalse(solid.contains(chosen), "trial " + trial + ": chose a solid cell");
                assertTrue(inRange(chosen, eyeX, eyeY, eyeZ), "trial " + trial + ": chose a cell outside maxDistance");
            } else {
                assertNull(chosen, "trial " + trial + ": nothing was open, yet a cell was chosen");
            }
        }
    }

    /**
     * Determinism: calling {@code choose} twice with byte-for-byte identical
     * arguments (including a fresh, unrelated predicate instance implementing
     * the same openness rule) must always return the same cell.
     */
    @Test
    void isDeterministicAcrossRepeatedCalls() {
        Random rng = new Random(SEED + 1);
        for (int trial = 0; trial < 50; trial++) {
            double eyeX = rng.nextDouble() * 4 - 2;
            double eyeY = 100 + rng.nextDouble() * 4 - 2;
            double eyeZ = rng.nextDouble() * 4 - 2;
            double[] dir = randomUnitVector(rng);
            long layoutSeed = rng.nextLong();

            Cell first = LightPlacement.choose(
                    eyeX, eyeY, eyeZ, dir[0], dir[1], dir[2],
                    MAX_DISTANCE, MIN_DISTANCE, null, STABILITY_BONUS,
                    cell -> pseudoRandomOpen(cell, layoutSeed));
            Cell second = LightPlacement.choose(
                    eyeX, eyeY, eyeZ, dir[0], dir[1], dir[2],
                    MAX_DISTANCE, MIN_DISTANCE, null, STABILITY_BONUS,
                    cell -> pseudoRandomOpen(cell, layoutSeed));

            assertEquals(first, second, "trial " + trial + ": identical inputs must choose the same cell");
        }
    }

    private static boolean pseudoRandomOpen(Cell cell, long layoutSeed) {
        long h = layoutSeed;
        h = h * 31 + cell.x();
        h = h * 31 + cell.y();
        h = h * 31 + cell.z();
        return new Random(h).nextDouble() > 0.5;
    }

    /**
     * Mirrors {@code LightPlacement}'s own range test: in-range uses the
     * closest point of the cell (not its centre) to the eye, so a cell whose
     * centre sits just past {@code MAX_DISTANCE} but that the ideal ray point
     * actually lands inside still counts.
     */
    private static boolean inRange(Cell cell, double eyeX, double eyeY, double eyeZ) {
        double nx = clamp(eyeX, cell.x(), cell.x() + 1.0);
        double ny = clamp(eyeY, cell.y(), cell.y() + 1.0);
        double nz = clamp(eyeZ, cell.z(), cell.z() + 1.0);
        double nearEdgeDist = Math.sqrt(sq(nx - eyeX) + sq(ny - eyeY) + sq(nz - eyeZ));
        if (nearEdgeDist > MAX_DISTANCE) {
            return false;
        }
        double dx = cell.x() + 0.5 - eyeX;
        double dy = cell.y() + 0.5 - eyeY;
        double dz = cell.z() + 0.5 - eyeZ;
        double centerDist = Math.sqrt(sq(dx) + sq(dy) + sq(dz));
        return centerDist >= MIN_DISTANCE;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double[] randomUnitVector(Random rng) {
        double x = rng.nextDouble() * 2 - 1;
        double y = rng.nextDouble() * 2 - 1;
        double z = rng.nextDouble() * 2 - 1;
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1.0e-6) {
            return new double[] {0, 0, 1};
        }
        return new double[] {x / len, y / len, z / len};
    }
}

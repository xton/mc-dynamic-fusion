package com.xton.fusion.wearable;

import java.util.function.Predicate;

/**
 * Pure geometry core for {@link GlowLightTask}: chooses which open cell near a
 * pair of eyes to place the client-side light in. No Bukkit dependency at
 * all, so it's exhaustively testable without spinning up a world.
 *
 * <p>The rule: among every open cell within {@code maxDistance} of the eye,
 * pick the one whose centre is nearest the "ideal" point straight ahead along
 * the look direction at {@code maxDistance} — so an unobstructed view always
 * picks directly ahead, and a blocked one backs off to whichever open cell is
 * geometrically closest to that ideal spot, in <em>any</em> direction, not
 * just forward/back along the look ray. That's what lets it find a cell in a
 * corner or under an overhang, where the straight-ahead line (and its
 * reverse, back through the head) is walled off in both directions but open
 * air still exists just to the side.
 *
 * <p>The eye position itself is never rounded before this search runs — only
 * the final candidate cells are (block light can only ever sit at integer
 * coordinates; that's a property of the mechanic, not a shortcut this search
 * takes). Deterministic: the same eye/direction/openness always chooses the
 * same cell, with ties broken by a fixed scan order. Passing the previously-lit
 * cell (if it's still open and in range) applies a small score bonus so
 * near-tied candidates don't flicker between ticks as the look direction
 * wobbles by a fraction of a degree.
 */
final class LightPlacement {

    /** Score difference below this counts as a tie, broken toward the farther cell. */
    private static final double TIE_EPSILON = 1.0e-9;

    private LightPlacement() {
    }

    /** An integer block cell, identified by its lower corner (Bukkit's own block-coordinate convention). */
    record Cell(int x, int y, int z) {

        double centerX() {
            return x + 0.5;
        }

        double centerY() {
            return y + 0.5;
        }

        double centerZ() {
            return z + 0.5;
        }
    }

    /**
     * @param eyeX, eyeY, eyeZ           the (continuous) eye position
     * @param dirX, dirY, dirZ           the (unit) look direction
     * @param maxDistance                both the ideal forward reach and the search radius
     * @param minDistance                cells closer than this to the eye are excluded (reads as "inside the face")
     * @param previous                   the previously-lit cell for this viewer, or null
     * @param stabilityBonus             score discount applied to {@code previous} so near-ties don't flicker
     * @param open                       true if a cell is open air fit to hold the light
     * @return the chosen cell, or null if nothing within range is open (fully embedded)
     */
    static Cell choose(double eyeX, double eyeY, double eyeZ,
                       double dirX, double dirY, double dirZ,
                       double maxDistance, double minDistance,
                       Cell previous, double stabilityBonus,
                       Predicate<Cell> open) {
        double idealX = eyeX + dirX * maxDistance;
        double idealY = eyeY + dirY * maxDistance;
        double idealZ = eyeZ + dirZ * maxDistance;

        int r = (int) Math.ceil(maxDistance);
        int ex = (int) Math.floor(eyeX);
        int ey = (int) Math.floor(eyeY);
        int ez = (int) Math.floor(eyeZ);

        Cell best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double bestEyeDist = -1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    Cell cell = new Cell(ex + dx, ey + dy, ez + dz);
                    double cx = cell.centerX();
                    double cy = cell.centerY();
                    double cz = cell.centerZ();

                    // In-range uses the closest point of the cell (not its
                    // centre) to the eye — otherwise a cell whose centre sits
                    // just past maxDistance, even though the ideal ray point
                    // itself lands inside that cell, would be wrongly excluded.
                    double nearEdgeDist = boxDistance(cell, eyeX, eyeY, eyeZ);
                    if (nearEdgeDist > maxDistance) {
                        continue;
                    }
                    double eyeDist = distance(cx, cy, cz, eyeX, eyeY, eyeZ);
                    if (eyeDist < minDistance) {
                        continue;
                    }
                    if (!open.test(cell)) {
                        continue;
                    }

                    double score = distance(cx, cy, cz, idealX, idealY, idealZ);
                    if (cell.equals(previous)) {
                        score -= stabilityBonus;
                    }
                    // A tie (eye/direction/distance landing exactly on a cell
                    // boundary) breaks toward the farther cell, so an
                    // unobstructed view reaches out to the full configured
                    // distance rather than an arbitrary scan-order artifact
                    // pulling it in short.
                    boolean better = score < bestScore - TIE_EPSILON
                            || (score < bestScore + TIE_EPSILON && eyeDist > bestEyeDist);
                    if (better) {
                        bestScore = score;
                        bestEyeDist = eyeDist;
                        best = cell;
                    }
                }
            }
        }
        return best;
    }

    private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Distance from {@code (px,py,pz)} to the nearest point of {@code cell}'s unit box (0 if inside it). */
    private static double boxDistance(Cell cell, double px, double py, double pz) {
        double nx = clamp(px, cell.x(), cell.x() + 1.0);
        double ny = clamp(py, cell.y(), cell.y() + 1.0);
        double nz = clamp(pz, cell.z(), cell.z() + 1.0);
        return distance(nx, ny, nz, px, py, pz);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(v, hi));
    }
}

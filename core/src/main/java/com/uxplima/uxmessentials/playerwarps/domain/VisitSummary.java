package com.uxplima.uxmessentials.playerwarps.domain;

/**
 * The denormalised rollup of how often a warp has been visited, kept on the aggregate so listings can show and
 * sort by popularity without scanning a visit-log table. {@link #count} is the total number of teleports to the
 * warp; {@link #uniqueVisitors} is how many distinct players are behind those teleports.
 *
 * @param count total visits
 * @param uniqueVisitors distinct players who have visited
 */
public record VisitSummary(long count, int uniqueVisitors) {

    public VisitSummary {
        if (count < 0) {
            throw new IllegalArgumentException("visit count must not be negative: " + count);
        }
        if (uniqueVisitors < 0) {
            throw new IllegalArgumentException("unique visitors must not be negative: " + uniqueVisitors);
        }
    }

    /** The rollup for a warp nobody has visited yet. */
    public static VisitSummary empty() {
        return new VisitSummary(0L, 0);
    }

    /**
     * Record one more visit by a player who has already been counted as unique. Bumping the unique-visitor tally
     * needs first-time knowledge the repository holds, so that count is left untouched here; this only advances
     * the raw total.
     */
    public VisitSummary incremented() {
        return new VisitSummary(count + 1, uniqueVisitors);
    }
}

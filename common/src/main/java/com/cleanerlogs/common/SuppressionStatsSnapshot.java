package com.cleanerlogs.common;

/**
 * Point-in-time view of {@link SuppressionStats}.
 *
 * @param loginSuppressed         suppressed login-phase disconnect lines
 * @param configurationSuppressed suppressed configuration-phase disconnect lines
 * @param totalSuppressed         suppressed disconnect lines in total
 */
public record SuppressionStatsSnapshot(long loginSuppressed, long configurationSuppressed, long totalSuppressed) {

    /** A snapshot of a runtime that has not suppressed anything yet. */
    public static final SuppressionStatsSnapshot EMPTY = new SuppressionStatsSnapshot(0L, 0L, 0L);

    /**
     * @return true when nothing has been suppressed
     */
    public boolean isEmpty() {
        return totalSuppressed == 0L;
    }
}

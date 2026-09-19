package com.cleanerlogs.common;

import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free counters for suppressed disconnects.
 *
 * <p>The counters are aggregate only. No addresses, player names, connection identifiers or event objects are
 * retained, so this can never grow into a leak and is safe to update from any Log4j2 thread.</p>
 */
public final class SuppressionStats {

    private final LongAdder loginSuppressed = new LongAdder();
    private final LongAdder configurationSuppressed = new LongAdder();
    private final LongAdder totalSuppressed = new LongAdder();

    /**
     * Records one suppressed event.
     *
     * @param phase the phase the suppressed event belonged to
     */
    public void recordSuppressed(ConnectionPhase phase) {
        totalSuppressed.increment();
        if (phase == ConnectionPhase.LOGIN) {
            loginSuppressed.increment();
        } else if (phase == ConnectionPhase.CONFIGURATION) {
            configurationSuppressed.increment();
        }
    }

    /**
     * @return an immutable snapshot of the counters
     */
    public SuppressionStatsSnapshot snapshot() {
        return new SuppressionStatsSnapshot(
            loginSuppressed.sum(),
            configurationSuppressed.sum(),
            totalSuppressed.sum());
    }

    /**
     * Resets every counter.
     */
    public void reset() {
        loginSuppressed.reset();
        configurationSuppressed.reset();
        totalSuppressed.reset();
    }
}

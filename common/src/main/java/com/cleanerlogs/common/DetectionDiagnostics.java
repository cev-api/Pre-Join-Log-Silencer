package com.cleanerlogs.common;

/**
 * Sink for {@link DetectionReport}s.
 *
 * <p>The detector asks {@link #isEnabled()} before building a report, so a disabled sink costs nothing on the
 * hot logging path.</p>
 */
public interface DetectionDiagnostics {

    /** A sink that is always disabled. */
    DetectionDiagnostics NOOP = new DetectionDiagnostics() {

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void report(DetectionReport report) {
            // intentionally empty
        }
    };

    /**
     * @return true when reports would actually be written somewhere
     */
    boolean isEnabled();

    /**
     * @param report the diagnostic snapshot
     */
    void report(DetectionReport report);
}

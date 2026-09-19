package com.cleanerlogs.common;

/**
 * Minimal logging surface the shared runtime uses.
 *
 * <p>Platform adapters map it onto whatever the server already exposes, so the common module never depends on
 * Bukkit or Fabric logging.</p>
 */
public interface RuntimeLog {

    /** A log that discards everything. */
    RuntimeLog NOOP = new RuntimeLog() {

        @Override
        public void info(String message) {
            // intentionally empty
        }

        @Override
        public void warn(String message) {
            // intentionally empty
        }

        @Override
        public void warn(String message, Throwable cause) {
            // intentionally empty
        }

        @Override
        public void debug(String message) {
            // intentionally empty
        }
    };

    /**
     * @param message the message to log at INFO level
     */
    void info(String message);

    /**
     * @param message the message to log at WARN level
     */
    void warn(String message);

    /**
     * @param message the message to log at WARN level
     * @param cause   the failure to attach
     */
    void warn(String message, Throwable cause);

    /**
     * @param message the message to log only while debug mode is enabled
     */
    void debug(String message);
}

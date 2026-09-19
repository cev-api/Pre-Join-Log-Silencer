package com.cleanerlogs.common;

/**
 * Immutable snapshot of the user configuration.
 *
 * <p>Snapshots are published through an atomic reference, so the filter can read the current settings from any
 * Log4j2 thread without locking and a reload never mutates a policy that is being read.</p>
 *
 * @param enabled                         master switch
 * @param suppressLoginDisconnects        suppress the pre-join disconnect line of the login phase
 * @param suppressConfigurationDisconnects suppress the pre-join disconnect line of the configuration phase
 * @param logSuppressedCount               write an infrequent aggregate summary line
 * @param logSuppressedToFile              copy every suppressed disconnect line into a separate file
 * @param debug                            emit per-event diagnostics
 */
public record SuppressionPolicy(
    boolean enabled,
    boolean suppressLoginDisconnects,
    boolean suppressConfigurationDisconnects,
    boolean logSuppressedCount,
    boolean logSuppressedToFile,
    boolean debug) {

    /** The shipped defaults: silence pre-join disconnects and stay quiet about it. */
    public static final SuppressionPolicy DEFAULT = new SuppressionPolicy(true, true, true, false, false, false);

    /**
     * @return the default policy
     */
    public static SuppressionPolicy defaults() {
        return DEFAULT;
    }

    /**
     * @param phase a detected connection phase
     * @return true when the current settings want disconnects of that phase suppressed
     */
    public boolean allows(ConnectionPhase phase) {
        if (!enabled || phase == null) {
            return false;
        }
        return switch (phase) {
            case LOGIN -> suppressLoginDisconnects;
            case CONFIGURATION -> suppressConfigurationDisconnects;
            case PLAY, UNKNOWN -> false;
        };
    }

    /**
     * @param value the new debug flag
     * @return a copy of this policy with debug replaced
     */
    public SuppressionPolicy withDebug(boolean value) {
        return new SuppressionPolicy(enabled, suppressLoginDisconnects, suppressConfigurationDisconnects,
            logSuppressedCount, logSuppressedToFile, value);
    }
}

package com.cleanerlogs.common;

/**
 * Notified from inside the Log4j2 filter whenever a disconnect line is suppressed.
 *
 * <p>Implementations run on whatever thread Log4j2 happened to use, must be fast, should not block and must
 * never throw. They must also never log through one of Minecraft's disconnect formats, otherwise they would
 * feed themselves.</p>
 */
@FunctionalInterface
public interface SuppressionListener {

    /** A listener that does nothing. */
    SuppressionListener NOOP = suppressed -> {
    };

    /**
     * @param suppressed a snapshot of the removed disconnect line
     */
    void onSuppressed(SuppressedDisconnect suppressed);
}

package com.cleanerlogs.bukkit;

import com.cleanerlogs.common.RuntimeLog;

/**
 * Maps the shared {@link RuntimeLog} onto the logger Bukkit already handed to the plugin.
 *
 * <p>CraftBukkit bridges {@code java.util.logging} into Log4j2 using the plugin name as the logger name, so
 * these messages and the plugin's own lifecycle messages end up on the logger that the identity matcher is told
 * to ignore.</p>
 */
final class BukkitRuntimeLog implements RuntimeLog {

    private final java.util.logging.Logger delegate;

    BukkitRuntimeLog(java.util.logging.Logger delegate) {
        this.delegate = delegate;
    }

    @Override
    public void info(String message) {
        delegate.info(message);
    }

    @Override
    public void warn(String message) {
        delegate.warning(message);
    }

    @Override
    public void warn(String message, Throwable cause) {
        delegate.log(java.util.logging.Level.WARNING, message, cause);
    }

    @Override
    public void debug(String message) {
        delegate.info("[debug] " + message);
    }
}

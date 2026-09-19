package com.cleanerlogs.fabric;

import com.cleanerlogs.common.RuntimeLog;
import com.cleanerlogs.common.log4j.Log4jDetectionDiagnostics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Maps the shared {@link RuntimeLog} onto the Log4j2 API.
 *
 * <p>The logger name is the same one the detector is told to ignore, so this project's own messages are never
 * classified and diagnostic output can never feed itself back into the filter.</p>
 */
final class Log4jRuntimeLog implements RuntimeLog {

    private final Logger delegate = LogManager.getLogger(Log4jDetectionDiagnostics.LOGGER_NAME);

    @Override
    public void info(String message) {
        delegate.info(message);
    }

    @Override
    public void warn(String message) {
        delegate.warn(message);
    }

    @Override
    public void warn(String message, Throwable cause) {
        delegate.warn(message, cause);
    }

    @Override
    public void debug(String message) {
        delegate.info("[debug] " + message);
    }
}

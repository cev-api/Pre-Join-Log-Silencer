package com.cleanerlogs.common.log4j;

import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.cleanerlogs.common.DetectionDiagnostics;
import com.cleanerlogs.common.DetectionReport;

/**
 * Writes {@link DetectionReport}s to a dedicated Log4j2 logger so debug mode works the same way on every
 * supported platform.
 *
 * <p>The logger name is registered as ignored by the logger identity matcher, and the diagnostic format string
 * contains no disconnect placeholder sequence, so diagnostic output can never be classified and therefore can
 * never feed itself back into the filter.</p>
 */
public final class Log4jDetectionDiagnostics implements DetectionDiagnostics {

    /**
     * Name of the logger debug output goes to. Also excluded from classification, and deliberately identical to
     * the Bukkit plugin name and the Fabric mod id so that this project's own messages are never inspected.
     */
    public static final String LOGGER_NAME = "PreJoinLogSilencer";

    private static final Logger DIAGNOSTICS = LogManager.getLogger(LOGGER_NAME);

    private final BooleanSupplier debugEnabled;

    /**
     * @param debugEnabled supplier of the live debug flag, read on every report
     */
    public Log4jDetectionDiagnostics(BooleanSupplier debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    @Override
    public boolean isEnabled() {
        try {
            return debugEnabled.getAsBoolean();
        } catch (RuntimeException error) {
            return false;
        }
    }

    @Override
    public void report(DetectionReport report) {
        DIAGNOSTICS.info(
            "[pre-join-log-silencer] phase={} basis={} suppressible={} reason=\"{}\" logger={} level={} "
                + "messageClass={} params={} format=\"{}\" formatted=\"{}\"",
            report.classification().phase(),
            report.classification().basis(),
            report.classification().suppressible(),
            report.classification().reason(),
            report.loggerName(),
            report.level(),
            report.messageClass(),
            report.parameterCount(),
            report.messageFormat(),
            report.formattedMessage());
    }
}

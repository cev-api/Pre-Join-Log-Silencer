package com.cleanerlogs.common;

import com.cleanerlogs.common.log4j.Log4jDetectionDiagnostics;
import com.cleanerlogs.common.log4j.Log4jFilterInstaller;
import com.cleanerlogs.common.log4j.PreJoinDisconnectFilter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wires the detector, the Log4j2 filter, the counters and the optional file copy together, and owns their life
 * cycle. Platform adapters only supply a logger identity table, a policy, a log and a file path.
 *
 * <p>No scheduler is used anywhere. The optional aggregate summary is emitted from inside the suppression path
 * on whatever thread Log4j2 already used, which keeps the runtime safe on Folia, where Bukkit's scheduler APIs
 * are not available.</p>
 */
public final class CleanerLogsRuntime {

    /**
     * Spacing between aggregate summary lines. One hour is often enough to see that the filter is working and
     * rare enough to keep a busy server's log readable. A summary is written only when something was actually
     * suppressed, so a quiet server writes none at all.
     */
    public static final Duration DEFAULT_SUMMARY_INTERVAL = Duration.ofHours(1L);

    private final LoggerIdentityMatcher loggerMatcher;
    private final AtomicReference<SuppressionPolicy> policy = new AtomicReference<>(SuppressionPolicy.DEFAULT);
    private final AtomicReference<RuntimeLog> log = new AtomicReference<>(RuntimeLog.NOOP);
    private final AtomicReference<Path> suppressedLogFile = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong summaryIntervalNanos = new AtomicLong(DEFAULT_SUMMARY_INTERVAL.toNanos());
    private final AtomicLong lastSummaryNanos = new AtomicLong(System.nanoTime());
    private final SuppressionStats stats = new SuppressionStats();

    private volatile PreJoinDisconnectFilter filter;
    private volatile SuppressedLogWriter writer;

    /**
     * @param loggerMatcher the logger identity table to detect with
     * @param initialPolicy the configuration to start with
     */
    public CleanerLogsRuntime(LoggerIdentityMatcher loggerMatcher, SuppressionPolicy initialPolicy) {
        this.loggerMatcher = Objects.requireNonNull(loggerMatcher, "loggerMatcher");
        this.policy.set(initialPolicy == null ? SuppressionPolicy.defaults() : initialPolicy);
    }

    /**
     * @param runtimeLog where lifecycle messages go, may be {@code null} to stay silent
     */
    public void setLog(RuntimeLog runtimeLog) {
        log.set(runtimeLog == null ? RuntimeLog.NOOP : runtimeLog);
    }

    /**
     * @param file where suppressed disconnect lines are copied when the configuration asks for it, may be
     *             {@code null} to disable the copy
     */
    public void setSuppressedLogFile(Path file) {
        suppressedLogFile.set(file);
    }

    /**
     * Sets the spacing between aggregate summary lines. A {@code null}, zero or negative duration removes the
     * throttle, which means one summary per suppressed event.
     *
     * @param interval the new interval, or {@code null} to remove the throttle
     */
    public void setSummaryInterval(Duration interval) {
        long nanos = interval == null ? 0L : interval.toNanos();
        summaryIntervalNanos.set(Math.max(nanos, 0L));
    }

    /**
     * @return the current spacing between aggregate summary lines
     */
    public Duration summaryInterval() {
        return Duration.ofNanos(summaryIntervalNanos.get());
    }

    /**
     * Replaces the live configuration and starts or stops the file copy if that setting changed. Safe to call at
     * any time, including while the runtime is running.
     *
     * @param newPolicy the new configuration
     */
    public void applyPolicy(SuppressionPolicy newPolicy) {
        policy.set(newPolicy == null ? SuppressionPolicy.defaults() : newPolicy);
        if (running.get()) {
            reconcileWriter();
        }
    }

    /**
     * @return the configuration currently in effect
     */
    public SuppressionPolicy policy() {
        return policy.get();
    }

    /**
     * @return the logger identity table in use
     */
    public LoggerIdentityMatcher loggerMatcher() {
        return loggerMatcher;
    }

    /**
     * @return true while the filter is installed
     */
    public boolean isRunning() {
        return running.get() && filter != null;
    }

    /**
     * @return an immutable snapshot of the suppression counters
     */
    public SuppressionStatsSnapshot stats() {
        return stats.snapshot();
    }

    /**
     * @return the file suppressed lines are copied to, or {@code null} when no file is configured
     */
    public Path suppressedLogFile() {
        return suppressedLogFile.get();
    }

    /**
     * @return true while suppressed lines are being copied to a file
     */
    public boolean isWritingSuppressedLog() {
        SuppressedLogWriter current = this.writer;
        return current != null && current.isRunning();
    }

    /**
     * @return how many lines were dropped because the writer queue was full
     */
    public long droppedSuppressedLines() {
        SuppressedLogWriter current = this.writer;
        return current == null ? 0L : current.droppedCount();
    }

    /**
     * Installs the filter. Idempotent: a second call while running is reported as success and does nothing.
     *
     * @return true when the filter is installed and active
     */
    public boolean start() {
        RuntimeLog runtimeLog = log.get();
        if (!running.compareAndSet(false, true)) {
            return isRunning();
        }

        SuppressionPolicy currentPolicy = policy.get();
        if (!currentPolicy.enabled()) {
            runtimeLog.info("Pre-join log silencing is disabled in the configuration; logging is unchanged.");
            running.set(false);
            return false;
        }

        try {
            Log4jDetectionDiagnostics diagnostics = new Log4jDetectionDiagnostics(this::isDebugEnabled);
            PreJoinDisconnectDetector detector = new PreJoinDisconnectDetector(loggerMatcher, diagnostics);
            PreJoinDisconnectFilter newFilter =
                PreJoinDisconnectFilter.create(detector, policy, this::onSuppressed);

            if (!Log4jFilterInstaller.install(newFilter)) {
                running.set(false);
                runtimeLog.warn("Could not install the Log4j2 filter, so pre-join disconnect messages will keep "
                    + "appearing. Server logging is unchanged.");
                return false;
            }

            this.filter = newFilter;
            // Only now that suppression is live does copying the lines to a file make sense.
            reconcileWriter();
            runtimeLog.info("Pre-join disconnect messages are now silenced. Debug mode is "
                + (currentPolicy.debug() ? "on" : "off") + ".");
            return true;
        } catch (RuntimeException | LinkageError error) {
            running.set(false);
            runtimeLog.warn("Could not install the Log4j2 filter, so pre-join disconnect messages will keep "
                + "appearing. Server logging is unchanged.", error);
            return false;
        }
    }

    /**
     * Removes the filter and stops the file copy. Idempotent. The filter is deactivated first, so logging is
     * restored even when the physical removal fails.
     */
    public void stop() {
        PreJoinDisconnectFilter current = this.filter;
        this.filter = null;
        running.set(false);
        stopWriter();

        if (current == null) {
            return;
        }
        current.deactivate();
        RuntimeLog runtimeLog = log.get();
        try {
            if (Log4jFilterInstaller.uninstall(current)) {
                SuppressionStatsSnapshot snapshot = stats.snapshot();
                runtimeLog.info("Pre-join disconnect filter removed. Suppressed "
                    + snapshot.totalSuppressed() + " message(s) while it was active.");
            } else {
                runtimeLog.warn("Could not remove the Log4j2 filter from the logging configuration; it has been "
                    + "deactivated instead, so logging behaves as if it had never been installed.");
            }
        } catch (RuntimeException | LinkageError error) {
            runtimeLog.warn("Could not remove the Log4j2 filter from the logging configuration; it has been "
                + "deactivated instead, so logging behaves as if it had never been installed.", error);
        }
    }

    /**
     * @return a one line description of the current state, used by the debug command and by reload logging
     */
    public String describeState() {
        SuppressionStatsSnapshot snapshot = stats.snapshot();
        StringBuilder description = new StringBuilder(isRunning() ? "installed" : "not installed");
        description.append(" | silenced ").append(snapshot.totalSuppressed())
            .append(" (").append(snapshot.loginSuppressed()).append(" login, ")
            .append(snapshot.configurationSuppressed()).append(" configuration)");
        description.append(" | ");
        if (isWritingSuppressedLog()) {
            description.append("copying to ").append(suppressedLogFile.get());
        } else {
            description.append("no file copy");
        }
        long dropped = droppedSuppressedLines();
        if (dropped > 0L) {
            description.append(" | ").append(dropped).append(" dropped");
        }
        return description.toString();
    }

    /**
     * @return the recognised logger identities, for debug output
     */
    public String describeMatcher() {
        StringBuilder description = new StringBuilder();
        for (ConnectionPhase phase : new ConnectionPhase[] {
            ConnectionPhase.LOGIN, ConnectionPhase.CONFIGURATION, ConnectionPhase.PLAY}) {
            if (description.length() > 0) {
                description.append("; ");
            }
            description.append(phase.name().toLowerCase(Locale.ROOT))
                .append('=')
                .append(loggerMatcher.simpleNames(phase));
        }
        return description.toString();
    }

    private boolean isDebugEnabled() {
        return policy.get().debug();
    }

    /**
     * Starts or stops the file copy so that it matches the current configuration.
     */
    private void reconcileWriter() {
        Path file = suppressedLogFile.get();
        if (policy.get().logSuppressedToFile() && file != null) {
            startWriter(file);
        } else {
            stopWriter();
        }
    }

    private void startWriter(Path file) {
        SuppressedLogWriter current = this.writer;
        if (current != null && current.isRunning() && file.equals(current.file())) {
            return;
        }
        stopWriter();
        SuppressedLogWriter newWriter = new SuppressedLogWriter(file, log.get());
        this.writer = newWriter;
        newWriter.start();
    }

    private void stopWriter() {
        SuppressedLogWriter current = this.writer;
        this.writer = null;
        if (current != null) {
            current.close();
        }
    }

    private void onSuppressed(SuppressedDisconnect suppressed) {
        stats.recordSuppressed(suppressed.phase());

        SuppressedLogWriter currentWriter = this.writer;
        if (currentWriter != null) {
            currentWriter.write(suppressed);
        }

        if (!policy.get().logSuppressedCount()) {
            return;
        }

        long now = System.nanoTime();
        long previous = lastSummaryNanos.get();
        if (now - previous < summaryIntervalNanos.get() || !lastSummaryNanos.compareAndSet(previous, now)) {
            return;
        }

        SuppressionStatsSnapshot snapshot = stats.snapshot();
        log.get().info("Silenced " + snapshot.totalSuppressed() + " pre-join disconnect message(s) so far ("
            + snapshot.loginSuppressed() + " login, " + snapshot.configurationSuppressed()
            + " configuration).");
    }
}

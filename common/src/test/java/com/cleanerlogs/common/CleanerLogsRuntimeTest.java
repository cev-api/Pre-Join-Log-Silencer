package com.cleanerlogs.common;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.cleanerlogs.common.log4j.Log4jFilterInstaller;
import com.cleanerlogs.common.log4j.PreJoinDisconnectFilter;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.CompositeFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CleanerLogsRuntimeTest {

    @Test
    @DisplayName("installs the filter on start and removes it on stop")
    void installsAndRemovesTheFilter() {
        CleanerLogsRuntime runtime = newRuntime(SuppressionPolicy.defaults());
        try {
            assertTrue(runtime.start());
            assertTrue(runtime.isRunning());
            assertNotNull(installedFilter(), "the filter must be present in the live configuration");
        } finally {
            runtime.stop();
        }

        assertAll(
            () -> assertFalse(runtime.isRunning()),
            () -> assertNull(installedFilter(), "the filter must be gone from the live configuration"));
    }

    @Test
    @DisplayName("double enable and double disable are harmless")
    void lifeCycleIsIdempotent() {
        CleanerLogsRuntime runtime = newRuntime(SuppressionPolicy.defaults());
        try {
            assertAll(
                () -> assertTrue(runtime.start()),
                () -> assertTrue(runtime.start(), "a second enable is reported as success"),
                () -> assertTrue(runtime.isRunning()));
        } finally {
            runtime.stop();
        }

        runtime.stop();
        assertFalse(runtime.isRunning());
    }

    @Test
    @DisplayName("a disabled runtime refuses to start and touches nothing")
    void refusesToStartWhenDisabled() {
        CleanerLogsRuntime runtime = newRuntime(LogEventFixtures.policy(false, true, true));

        assertAll(
            () -> assertFalse(runtime.start()),
            () -> assertFalse(runtime.isRunning()),
            () -> assertNull(installedFilter()));
    }

    @Test
    @DisplayName("policy changes take effect on the installed filter without reinstalling")
    void appliesPolicyChanges() {
        CleanerLogsRuntime runtime = newRuntime(SuppressionPolicy.defaults());
        try {
            assertTrue(runtime.start());
            PreJoinDisconnectFilter filter = installedFilter();
            assertNotNull(filter);

            assertEquals(Filter.Result.DENY, filter.filter(loginEvent()));

            runtime.applyPolicy(LogEventFixtures.policy(true, false, true));
            assertEquals(Filter.Result.NEUTRAL, filter.filter(loginEvent()));

            runtime.applyPolicy(LogEventFixtures.policy(true, true, true));
            assertEquals(Filter.Result.DENY, filter.filter(loginEvent()));

            assertEquals(2L, runtime.stats().totalSuppressed());
        } finally {
            runtime.stop();
        }
    }

    @Test
    @DisplayName("a stopped runtime leaves the previously installed filter permanently inert")
    void stoppedFilterIsInert() {
        CleanerLogsRuntime runtime = newRuntime(SuppressionPolicy.defaults());
        assertTrue(runtime.start());
        PreJoinDisconnectFilter filter = installedFilter();
        assertNotNull(filter);

        runtime.stop();

        assertEquals(Filter.Result.NEUTRAL, filter.filter(loginEvent()));
    }

    @Test
    @DisplayName("lifecycle messages go to the configured log")
    void reportsToTheConfiguredLog() {
        List<String> messages = new ArrayList<>();
        CleanerLogsRuntime runtime = newRuntime(SuppressionPolicy.defaults());
        runtime.setLog(new RecordingLog(messages));

        try {
            assertTrue(runtime.start());
        } finally {
            runtime.stop();
        }

        assertTrue(messages.stream().anyMatch(message -> message.contains("silenced")),
            "the log must mention that silencing is active: " + messages);
    }

    @Test
    @DisplayName("suppressed lines are copied to a file when the configuration asks for it")
    void writesSuppressedLinesToAFile(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("suppressed.log");
        CleanerLogsRuntime runtime = newRuntime(
            new SuppressionPolicy(true, true, true, false, true, false));
        runtime.setSuppressedLogFile(file);

        try {
            assertTrue(runtime.start());
            assertTrue(runtime.isWritingSuppressedLog());

            PreJoinDisconnectFilter filter = installedFilter();
            assertNotNull(filter);
            filter.filter(loginEvent());
            filter.filter(playEvent());
        } finally {
            runtime.stop();
        }

        assertFalse(runtime.isWritingSuppressedLog());
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertAll(
            () -> assertTrue(content.contains("Probe (/1.2.3.4:5) lost connection: Disconnected"),
                "the suppressed line must be in the file: " + content),
            () -> assertTrue(content.contains("[login]"), content),
            () -> assertFalse(content.contains("NecoConneco"),
                "a PLAY disconnect must never be written to this file"),
            () -> assertEquals(0L, runtime.droppedSuppressedLines()));
    }

    @Test
    @DisplayName("the file copy follows the configuration across a reload")
    void fileCopyFollowsTheConfiguration(@TempDir Path directory) {
        Path file = directory.resolve("suppressed.log");
        CleanerLogsRuntime runtime = newRuntime(SuppressionPolicy.defaults());
        runtime.setSuppressedLogFile(file);

        try {
            assertTrue(runtime.start());
            assertFalse(runtime.isWritingSuppressedLog(), "the default policy writes no file");

            runtime.applyPolicy(new SuppressionPolicy(true, true, true, false, true, false));
            assertTrue(runtime.isWritingSuppressedLog(), "enabling the key must open the file");

            runtime.applyPolicy(SuppressionPolicy.defaults());
            assertFalse(runtime.isWritingSuppressedLog(), "disabling the key must close the file");
        } finally {
            runtime.stop();
        }
    }

    @Test
    @DisplayName("no file copy happens when no path was supplied")
    void noFileCopyWithoutAPath() {
        CleanerLogsRuntime runtime = newRuntime(new SuppressionPolicy(true, true, true, false, true, false));

        try {
            assertTrue(runtime.start());
            assertFalse(runtime.isWritingSuppressedLog());
        } finally {
            runtime.stop();
        }
    }

    @Test
    @DisplayName("the summary is written when it is enabled and the interval has elapsed")
    void writesTheSummary() {
        List<String> messages = new ArrayList<>();
        CleanerLogsRuntime runtime = newRuntime(new SuppressionPolicy(true, true, true, true, false, false));
        runtime.setLog(new RecordingLog(messages));
        // Removing the throttle keeps the test independent of wall clock time.
        runtime.setSummaryInterval(null);

        try {
            assertTrue(runtime.start());
            assertNotNull(installedFilter());
            installedFilter().filter(loginEvent());
        } finally {
            runtime.stop();
        }

        assertAll(
            () -> assertTrue(
                messages.stream().anyMatch(message -> message.contains("Silenced 1 pre-join disconnect")),
                messages.toString()),
            () -> assertTrue(messages.stream().noneMatch(message -> message.contains("Genuine")),
                "the summary must not carry commentary: " + messages));
    }

    @Test
    @DisplayName("no summary is written when the setting is off")
    void staysSilentWhenTheSummaryIsOff() {
        List<String> messages = new ArrayList<>();
        CleanerLogsRuntime runtime = newRuntime(SuppressionPolicy.defaults());
        runtime.setLog(new RecordingLog(messages));
        runtime.setSummaryInterval(null);

        try {
            assertTrue(runtime.start());
            assertNotNull(installedFilter());
            installedFilter().filter(loginEvent());
        } finally {
            runtime.stop();
        }

        assertTrue(messages.stream().noneMatch(message -> message.contains("Silenced 1")), messages.toString());
    }

    @Test
    @DisplayName("the default summary interval is one hour")
    void defaultSummaryIntervalIsOneHour() {
        CleanerLogsRuntime runtime = newRuntime(SuppressionPolicy.defaults());

        assertEquals(java.time.Duration.ofHours(1L), runtime.summaryInterval());
    }

    @Test
    @DisplayName("the state description is one compact line")
    void describesState(@TempDir Path directory) {
        Path file = directory.resolve("suppressed.log");
        CleanerLogsRuntime runtime = newRuntime(new SuppressionPolicy(true, true, true, false, true, false));
        runtime.setSuppressedLogFile(file);

        try {
            assertTrue(runtime.start());
            assertNotNull(installedFilter());
            installedFilter().filter(loginEvent());

            String description = runtime.describeState();
            assertAll(
                () -> assertTrue(description.contains("installed"), description),
                () -> assertTrue(description.contains("silenced 1 (1 login, 0 configuration)"), description),
                () -> assertTrue(description.contains(file.toString()), description),
                () -> assertFalse(description.contains("\n"), description),
                // Exactly three fields: state, counters, file copy.
                () -> assertEquals(3, description.split("\\|").length, description));
        } finally {
            runtime.stop();
        }
    }

    private static CleanerLogsRuntime newRuntime(SuppressionPolicy policy) {
        return new CleanerLogsRuntime(MinecraftLoggerNames.defaultMatcher(), policy);
    }

    private static PreJoinDisconnectFilter installedFilter() {
        LoggerContext context = Log4jFilterInstaller.currentContext();
        return context == null ? null : find(context.getConfiguration().getRootLogger().getFilter());
    }

    private static PreJoinDisconnectFilter find(Filter chain) {
        if (chain == null) {
            return null;
        }
        if (chain instanceof PreJoinDisconnectFilter filter) {
            return filter;
        }
        if (chain instanceof CompositeFilter composite) {
            for (Filter child : composite.getFiltersArray()) {
                PreJoinDisconnectFilter found = find(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static LogEvent loginEvent() {
        return LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", "Disconnected"));
    }

    private static LogEvent playEvent() {
        return LogEventFixtures.info(LogEventFixtures.PLAY_LOGGER,
            LogEventFixtures.lostConnection("NecoConneco", "Disconnected"));
    }

    /**
     * Collects lifecycle messages instead of writing them to the console.
     */
    private static final class RecordingLog implements RuntimeLog {

        private final List<String> messages;

        private RecordingLog(List<String> messages) {
            this.messages = messages;
        }

        @Override
        public void info(String message) {
            messages.add(message);
        }

        @Override
        public void warn(String message) {
            messages.add("WARN " + message);
        }

        @Override
        public void warn(String message, Throwable cause) {
            messages.add("WARN " + message + " / " + cause);
        }

        @Override
        public void debug(String message) {
            messages.add("DEBUG " + message);
        }
    }
}

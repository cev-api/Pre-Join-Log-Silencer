package com.cleanerlogs.fabric;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cleanerlogs.common.RuntimeLog;
import com.cleanerlogs.common.SuppressionPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the Fabric-only defaults. Fabric has no command, so the hourly summary is the operator's only view of
 * the suppression counters, and it must not be switched off by accident.
 */
class FabricSettingsTest {

    @Test
    @DisplayName("a missing file is created and yields the Fabric defaults")
    void writesAndReturnsTheDefaults(@TempDir Path directory) throws IOException {
        List<String> messages = new ArrayList<>();
        SuppressionPolicy policy = FabricSettings.load(directory, new RecordingLog(messages));

        Path file = directory.resolve("prejoin-log-silencer.properties");
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertAll(
            () -> assertTrue(Files.isRegularFile(file), "the default file must be created"),
            () -> assertTrue(written.contains("log-suppressed-count=true"),
                "the written default must match the policy: " + written),
            () -> assertTrue(written.contains("log-suppressed-to-file=false"), written),
            () -> assertTrue(written.contains("enabled=true"), written),
            () -> assertTrue(messages.stream().anyMatch(message -> message.contains("default configuration")),
                messages.toString()));
    }

    @Test
    @DisplayName("the hourly summary is on by default because Fabric has no command")
    void summaryIsOnByDefault(@TempDir Path directory) {
        SuppressionPolicy policy = FabricSettings.load(directory, RuntimeLog.NOOP);

        assertAll(
            () -> assertTrue(policy.enabled()),
            () -> assertTrue(policy.suppressLoginDisconnects()),
            () -> assertTrue(policy.suppressConfigurationDisconnects()),
            () -> assertTrue(policy.logSuppressedCount(), "the summary must default to on"),
            () -> assertFalse(policy.logSuppressedToFile(), "the file copy must default to off"),
            () -> assertFalse(policy.debug()));
    }

    @Test
    @DisplayName("the built-in defaults agree with the file that is written")
    void defaultsAgreeWithTheWrittenFile(@TempDir Path directory) throws IOException {
        SuppressionPolicy fromDefaults = FabricSettings.load(directory, RuntimeLog.NOOP);
        SuppressionPolicy fromFile = FabricSettings.load(directory, RuntimeLog.NOOP);

        assertEquals(fromDefaults, fromFile, "reading back the written file must reproduce the defaults");
    }

    @Test
    @DisplayName("an explicit value overrides the default while other keys keep theirs")
    void explicitValuesOverrideTheDefaults(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("prejoin-log-silencer.properties");
        Files.writeString(file, """
            log-suppressed-count=false
            debug=true
            """, StandardCharsets.UTF_8);

        SuppressionPolicy policy = FabricSettings.load(directory, RuntimeLog.NOOP);

        assertAll(
            () -> assertFalse(policy.logSuppressedCount(), "an explicit false must win"),
            () -> assertTrue(policy.debug(), "an explicit true must win"),
            () -> assertTrue(policy.enabled(), "an absent key keeps its default"),
            () -> assertTrue(policy.suppressLoginDisconnects(), "an absent key keeps its default"));
    }

    @Test
    @DisplayName("a directory in place of the file falls back to the defaults instead of throwing")
    void unreadableFileFallsBack(@TempDir Path directory) throws IOException {
        List<String> messages = new ArrayList<>();
        Files.createDirectory(directory.resolve("prejoin-log-silencer.properties"));

        SuppressionPolicy policy = FabricSettings.load(directory, new RecordingLog(messages));

        assertAll(
            () -> assertTrue(policy.logSuppressedCount(), "the Fabric default must still apply"),
            () -> assertTrue(messages.stream().anyMatch(message -> message.startsWith("WARN")),
                "the failure must be reported: " + messages));
    }

    /**
     * Collects messages instead of writing them to the console.
     */
    private static final class RecordingLog implements RuntimeLog {

        private final List<String> messages;

        private RecordingLog(List<String> messages) {
            this.messages = messages;
        }

        @Override
        public void info(String message) {
            messages.add("INFO " + message);
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

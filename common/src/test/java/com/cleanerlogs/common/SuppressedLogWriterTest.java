package com.cleanerlogs.common;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SuppressedLogWriterTest {

    @Test
    @DisplayName("writes each suppressed line with a timestamp and its phase")
    void writesLines(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("suppressed.log");
        try (SuppressedLogWriter writer = new SuppressedLogWriter(file, RuntimeLog.NOOP)) {
            assertTrue(writer.start());
            writer.write(suppressed("Probe (/32.188.152.209:51894)", "Disconnected", ConnectionPhase.LOGIN));
            writer.write(suppressed("NecoConneco", "Timed out", ConnectionPhase.CONFIGURATION));
        }

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertAll(
            () -> assertTrue(content.contains("Probe (/32.188.152.209:51894) lost connection: Disconnected"),
                content),
            () -> assertTrue(content.contains("[login]"), content),
            () -> assertTrue(content.contains("[configuration]"), content),
            () -> assertTrue(content.contains("NecoConneco lost connection: Timed out"), content));
    }

    @Test
    @DisplayName("appends to an existing file instead of replacing it")
    void appendsToAnExistingFile(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("suppressed.log");

        try (SuppressedLogWriter writer = new SuppressedLogWriter(file, RuntimeLog.NOOP)) {
            assertTrue(writer.start());
            writer.write(suppressed("First", "Disconnected", ConnectionPhase.LOGIN));
        }
        try (SuppressedLogWriter writer = new SuppressedLogWriter(file, RuntimeLog.NOOP)) {
            assertTrue(writer.start());
            writer.write(suppressed("Second", "Disconnected", ConnectionPhase.LOGIN));
        }

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertAll(
            () -> assertTrue(content.contains("First lost connection: Disconnected"), content),
            () -> assertTrue(content.contains("Second lost connection: Disconnected"), content));
    }

    @Test
    @DisplayName("creating the directory and the file is the writer's job")
    void createsMissingDirectories(@TempDir Path directory) {
        Path file = directory.resolve("nested").resolve("deeper").resolve("suppressed.log");

        try (SuppressedLogWriter writer = new SuppressedLogWriter(file, RuntimeLog.NOOP)) {
            assertTrue(writer.start());
            writer.write(suppressed("Probe", "Disconnected", ConnectionPhase.LOGIN));
        }

        assertTrue(Files.isRegularFile(file));
    }

    @Test
    @DisplayName("writing before start and after close is silently ignored")
    void ignoresWritesOutsideTheLifeCycle(@TempDir Path directory) {
        Path file = directory.resolve("suppressed.log");
        SuppressedLogWriter writer = new SuppressedLogWriter(file, RuntimeLog.NOOP);

        writer.write(suppressed("Before", "Disconnected", ConnectionPhase.LOGIN));
        writer.close();

        assertFalse(writer.isRunning());
        assertAll(
            () -> assertFalse(Files.exists(file)),
            () -> assertEquals(0L, writer.droppedCount()));
    }

    @Test
    @DisplayName("start and close are idempotent, and null writes never throw")
    void lifeCycleIsIdempotent(@TempDir Path directory) {
        Path file = directory.resolve("suppressed.log");
        SuppressedLogWriter writer = new SuppressedLogWriter(file, RuntimeLog.NOOP);

        try {
            assertTrue(writer.start());
            assertTrue(writer.start());
            writer.write(null);
        } finally {
            writer.close();
            writer.close();
        }

        assertFalse(writer.isRunning());
        assertEquals(file, writer.file());
    }

    @Test
    @DisplayName("an unwritable destination is reported and never throws at the caller")
    void reportsAnUnwritableDestination(@TempDir Path directory) throws IOException {
        List<String> messages = new ArrayList<>();
        RuntimeLog log = new RecordingLog(messages);
        // An existing directory can never be opened as a file, so the writer has to cope with the failure.
        Path file = Files.createDirectory(directory.resolve("a-directory"));

        try (SuppressedLogWriter writer = new SuppressedLogWriter(file, log)) {
            assertTrue(writer.start());
            writer.write(suppressed("Probe", "Disconnected", ConnectionPhase.LOGIN));
            writer.write(suppressed("Probe", "Disconnected", ConnectionPhase.LOGIN));
        }

        assertTrue(messages.stream().anyMatch(message -> message.startsWith("WARN")),
            "the failure must be reported: " + messages);
    }

    @Test
    @DisplayName("the snapshot rebuilds both Minecraft disconnect shapes")
    void rebuildsBothShapes() {
        SuppressedDisconnect login = SuppressedDisconnect.of(
            ConnectionPhase.LOGIN,
            "a.b.LoginListener",
            LostConnectionFormat.IDENTIFIER_AND_REASON,
            new ParameterizedMessage("{} lost connection: {}", "Probe (/1.2.3.4:5)", "Disconnected").getParameters());

        SuppressedDisconnect configuration = SuppressedDisconnect.of(
            ConnectionPhase.CONFIGURATION,
            "a.b.ConfigurationListener",
            LostConnectionFormat.NAME_UUID_AND_REASON,
            new ParameterizedMessage("{} ({}) lost connection: {}", "NecoConneco", "uuid", "Timed out")
                .getParameters());

        SuppressedDisconnect missingParameters = SuppressedDisconnect.of(
            ConnectionPhase.LOGIN, "a.b.LoginListener", LostConnectionFormat.IDENTIFIER_AND_REASON, null);

        assertAll(
            () -> assertEquals("Probe (/1.2.3.4:5) lost connection: Disconnected", login.describe()),
            () -> assertEquals("NecoConneco (uuid) lost connection: Timed out", configuration.describe()),
            () -> assertEquals("<unknown> lost connection: <unknown>", missingParameters.describe()));
    }

    private static SuppressedDisconnect suppressed(String subject, String reason, ConnectionPhase phase) {
        return new SuppressedDisconnect(phase, "a.b.Listener", subject, reason);
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

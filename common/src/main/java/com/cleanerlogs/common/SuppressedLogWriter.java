package com.cleanerlogs.common;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Copies every suppressed disconnect line into a separate file, so nothing is really lost.
 *
 * <p>Suppression happens on Minecraft's network threads. Writing a file there would be unacceptable, so events
 * are handed to a bounded queue and drained by one daemon thread. {@link #write} never blocks and never throws:
 * if the queue is full the line is dropped and counted, because a busy server must not lose tick time over a
 * log line.</p>
 */
public final class SuppressedLogWriter implements AutoCloseable {

    private static final int QUEUE_CAPACITY = 8192;
    private static final int MAX_QUEUE_WAIT_MILLIS = 500;
    private static final long SHUTDOWN_JOIN_MILLIS = 2000L;

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String HEADER = """
        # Pre-Join Log Silencer - suppressed disconnect lines
        # These lines were removed from the console and from the normal log files.
        # Genuine player disconnects are never written here, because they are never suppressed.
        """;

    private final Path file;
    private final RuntimeLog log;
    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final LongAdder dropped = new LongAdder();
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile Thread worker;

    /**
     * @param file where suppressed lines are appended
     * @param log  where problems are reported
     */
    public SuppressedLogWriter(Path file, RuntimeLog log) {
        this.file = Objects.requireNonNull(file, "file");
        this.log = log == null ? RuntimeLog.NOOP : log;
    }

    /**
     * Starts the writer thread. Idempotent.
     *
     * @return true when the file is open and lines will be written
     */
    public boolean start() {
        if (!running.compareAndSet(false, true)) {
            return true;
        }
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException error) {
            running.set(false);
            log.warn("Could not create the directory for " + file + "; suppressed lines are not written "
                + "to a file.", error);
            return false;
        }

        Thread thread = new Thread(this::drainQueue, "PreJoinLogSilencer-writer");
        // A daemon thread can never hold up server shutdown.
        thread.setDaemon(true);
        this.worker = thread;
        thread.start();
        log.info("Suppressed disconnect lines are also written to " + file + ".");
        return true;
    }

    /**
     * Queues one line. Never blocks, never throws.
     *
     * @param suppressed the suppressed disconnect
     */
    public void write(SuppressedDisconnect suppressed) {
        if (!running.get() || suppressed == null) {
            return;
        }
        String line = TIMESTAMP.format(LocalDateTime.now())
            + " [" + suppressed.phase().name().toLowerCase(java.util.Locale.ROOT) + "] "
            + suppressed.describe();
        if (!queue.offer(line)) {
            dropped.increment();
        }
    }

    /**
     * @return how many lines were dropped because the queue was full
     */
    public long droppedCount() {
        return dropped.sum();
    }

    /**
     * @return the file lines are written to
     */
    public Path file() {
        return file;
    }

    /**
     * @return true while the writer thread accepts lines
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Flushes what is left and stops the writer thread. Idempotent.
     */
    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        Thread thread = this.worker;
        this.worker = null;
        if (thread != null) {
            try {
                thread.join(SHUTDOWN_JOIN_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void drainQueue() {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (Files.size(file) == 0L) {
                writer.write(HEADER);
                writer.newLine();
                writer.flush();
            }
            while (running.get() || !queue.isEmpty()) {
                String first = queue.poll(MAX_QUEUE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                writeLine(writer, first);
                // Drain in a batch so a burst of connections causes one flush, not hundreds.
                String next;
                while ((next = queue.poll()) != null) {
                    writeLine(writer, next);
                }
                writer.flush();
            }
        } catch (IOException error) {
            stopAfterFailure(error);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }

    private void stopAfterFailure(IOException error) {
        // Report once, then stay silent: a full disk must not fill the log with the same warning.
        running.set(false);
        queue.clear();
        log.warn("Could not write to " + file + "; suppressed lines are no longer copied to a file.", error);
    }
}

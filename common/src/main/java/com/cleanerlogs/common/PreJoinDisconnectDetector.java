package com.cleanerlogs.common;

import java.util.Locale;
import java.util.Objects;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.Message;

/**
 * Decides whether a single Log4j2 event is the INFO "lost connection" line of a connection that disconnected
 * before it ever reached PLAY.
 *
 * <p>The detector is immutable, allocation free on the paths that matter and therefore safe to call from any
 * Log4j2 thread. It never keeps a reference to an event, a message, a player, a channel or a connection.</p>
 *
 * <p>Rules, in order of precedence:</p>
 * <ol>
 *   <li>A known PLAY logger is never suppressed, whatever the message looks like.</li>
 *   <li>Only INFO events without a throwable are considered.</li>
 *   <li>The event must be one of Minecraft's parameterised "lost connection" messages. A plugin that merely
 *       prints those words can never match, because it has no placeholders to match against.</li>
 *   <li>A known login or configuration logger identity is then enough to decide.</li>
 *   <li>For unknown logger identities a conservative structural check on the message has to pass as well, and
 *       the logger has to belong to Minecraft.</li>
 * </ol>
 *
 * <p>False negatives are always preferred over false positives: whenever the detector is not certain, the event
 * is let through.</p>
 */
public final class PreJoinDisconnectDetector {

    private static final String MINECRAFT_PACKAGE_MARKER = ".minecraft.";

    private final LoggerIdentityMatcher loggerMatcher;
    private final DetectionDiagnostics diagnostics;

    /**
     * @param loggerMatcher the logger identity table, usually built from {@link MinecraftLoggerNames}
     * @param diagnostics   where debug output goes, may be {@code null} or disabled
     */
    public PreJoinDisconnectDetector(LoggerIdentityMatcher loggerMatcher, DetectionDiagnostics diagnostics) {
        this.loggerMatcher = Objects.requireNonNull(loggerMatcher, "loggerMatcher");
        this.diagnostics = diagnostics == null ? DetectionDiagnostics.NOOP : diagnostics;
    }

    /**
     * @param event a Log4j2 event, may be {@code null}
     * @return true only when there is strong evidence that this event belongs to a connection that never reached
     *         PLAY
     */
    public boolean shouldSuppress(LogEvent event) {
        return classify(event).suppressible();
    }

    /**
     * Full verdict for one event, including the reason. Used by debug mode and by the tests.
     *
     * @param event a Log4j2 event, may be {@code null}
     * @return the classification, never {@code null}
     */
    public PreJoinClassification classify(LogEvent event) {
        PreJoinClassification classification = evaluate(event);
        if (diagnostics.isEnabled()) {
            report(event, classification);
        }
        return classification;
    }

    private PreJoinClassification evaluate(LogEvent event) {
        if (event == null) {
            return PreJoinClassification.notSuppressible(ConnectionPhase.UNKNOWN, DetectionBasis.NONE,
                "event is null");
        }

        String loggerName = event.getLoggerName();
        if (loggerName == null || loggerName.isEmpty()) {
            return PreJoinClassification.notSuppressible(ConnectionPhase.UNKNOWN, DetectionBasis.NONE,
                "event has no logger name");
        }
        if (loggerMatcher.isIgnored(loggerName)) {
            return PreJoinClassification.notSuppressible(ConnectionPhase.UNKNOWN, DetectionBasis.NONE,
                "logger is excluded from classification");
        }

        ConnectionPhase phase = loggerMatcher.classify(loggerName);
        if (phase == ConnectionPhase.PLAY) {
            // Hard veto: this identity always means a real player session.
            return PreJoinClassification.notSuppressible(phase, DetectionBasis.LOGGER_IDENTITY,
                "logger " + loggerName + " serves connections that reached PLAY");
        }
        if (!Level.INFO.equals(event.getLevel())) {
            return PreJoinClassification.notSuppressible(phase, DetectionBasis.NONE,
                "level is " + event.getLevel() + ", only INFO disconnect lines are considered");
        }
        if (event.getThrown() != null) {
            return PreJoinClassification.notSuppressible(phase, DetectionBasis.NONE,
                "event carries a throwable, so it is not a plain disconnect line");
        }

        Message message = event.getMessage();
        LostConnectionFormat format = DisconnectMessages.formatOf(message);
        if (format == null) {
            return PreJoinClassification.notSuppressible(phase, DetectionBasis.NONE,
                "message is not one of Minecraft's parameterised disconnect formats");
        }

        Object[] parameters = DisconnectMessages.parametersOf(message);
        int parameterCount = parameters == null ? 0 : parameters.length;
        if (parameterCount < format.requiredParameterCount()) {
            return PreJoinClassification.notSuppressible(phase, DetectionBasis.NONE,
                "format '" + format.prefix() + "' needs " + format.requiredParameterCount()
                    + " message parameters but the event carries " + parameterCount);
        }

        if (phase == ConnectionPhase.LOGIN) {
            return PreJoinClassification.suppressible(ConnectionPhase.LOGIN, DetectionBasis.LOGGER_IDENTITY,
                "logger " + loggerName + " only serves connections that have not reached PLAY");
        }
        if (phase == ConnectionPhase.CONFIGURATION) {
            return PreJoinClassification.suppressible(ConnectionPhase.CONFIGURATION, DetectionBasis.LOGGER_IDENTITY,
                "logger " + loggerName + " only serves connections that have not reached PLAY");
        }
        return classifyByMessageStructure(loggerName, format, parameters);
    }

    private PreJoinClassification classifyByMessageStructure(String loggerName,
                                                            LostConnectionFormat format,
                                                            Object[] parameters) {
        if (!looksLikeMinecraftLogger(loggerName)) {
            return PreJoinClassification.notSuppressible(ConnectionPhase.UNKNOWN, DetectionBasis.MESSAGE_STRUCTURE,
                "unknown logger is not a Minecraft logger, so its disconnect-shaped message is left alone");
        }

        if (format == LostConnectionFormat.NAME_UUID_AND_REASON) {
            String uuid = parameters.length > 1 ? asText(parameters[1]) : null;
            if (!PrePlayIdentifierMatcher.isUuid(uuid)) {
                return PreJoinClassification.notSuppressible(ConnectionPhase.UNKNOWN,
                    DetectionBasis.MESSAGE_STRUCTURE,
                    "second parameter is not a game profile UUID, so this is not the configuration-phase line");
            }
            return PreJoinClassification.suppressible(ConnectionPhase.CONFIGURATION,
                DetectionBasis.MESSAGE_STRUCTURE,
                "unknown Minecraft logger " + loggerName
                    + " emitted the configuration-phase disconnect shape (name plus profile UUID)");
        }

        String identifier = asText(parameters[0]);
        if (!PrePlayIdentifierMatcher.isPrePlayIdentifier(identifier)) {
            return PreJoinClassification.notSuppressible(ConnectionPhase.UNKNOWN, DetectionBasis.MESSAGE_STRUCTURE,
                "first parameter is a player name rather than a pre-join connection identifier");
        }
        return PreJoinClassification.suppressible(ConnectionPhase.LOGIN, DetectionBasis.MESSAGE_STRUCTURE,
            "unknown Minecraft logger " + loggerName
                + " emitted a disconnect line identified by a remote connection instead of a player name");
    }

    private static boolean looksLikeMinecraftLogger(String loggerName) {
        return loggerName.toLowerCase(Locale.ROOT).contains(MINECRAFT_PACKAGE_MARKER);
    }

    private void report(LogEvent event, PreJoinClassification classification) {
        if (event == null || !isWorthReporting(event, classification)) {
            return;
        }
        Message message = event.getMessage();
        String format = message == null ? null : message.getFormat();
        Object[] parameters = message == null ? null : message.getParameters();
        diagnostics.report(new DetectionReport(
            event.getLoggerName(),
            String.valueOf(event.getLevel()),
            message == null ? null : message.getClass().getSimpleName(),
            format,
            safeFormattedMessage(message),
            parameters == null ? -1 : parameters.length,
            classification));
    }

    private static boolean isWorthReporting(LogEvent event, PreJoinClassification classification) {
        if (classification.suppressible() || classification.phase() != ConnectionPhase.UNKNOWN) {
            return true;
        }
        String format = event.getMessage() == null ? null : event.getMessage().getFormat();
        return format != null && format.contains(LostConnectionFormat.DISCONNECT_MARKER);
    }

    private static String safeFormattedMessage(Message message) {
        if (message == null) {
            return null;
        }
        try {
            return message.getFormattedMessage();
        } catch (RuntimeException error) {
            return "<unavailable: " + error.getClass().getSimpleName() + ">";
        }
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString();
    }
}

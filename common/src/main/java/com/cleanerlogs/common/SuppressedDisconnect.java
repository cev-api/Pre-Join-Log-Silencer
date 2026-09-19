package com.cleanerlogs.common;

import java.util.Objects;

/**
 * An immutable snapshot of one suppressed disconnect.
 *
 * <p>Built only after a disconnect line has already been classified as suppressible, and handed straight to the
 * listeners. Nothing here is retained, so no connection, player, address or event is ever held.</p>
 *
 * @param phase      the phase the connection was in when it stopped
 * @param loggerName the Log4j2 logger that emitted the line
 * @param subject    the connection identifier, as Minecraft printed it
 * @param reason     the disconnect reason
 */
public record SuppressedDisconnect(ConnectionPhase phase, String loggerName, String subject, String reason) {

    private static final String UNKNOWN = "<unknown>";

    public SuppressedDisconnect {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(loggerName, "loggerName");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(reason, "reason");
    }

    /**
     * Rebuilds the original disconnect line from the parameters of a matched log event.
     *
     * @param phase      the phase the connection was in
     * @param loggerName the emitting logger
     * @param format     the matched disconnect shape
     * @param parameters the message parameters of the event
     * @return the snapshot
     */
    public static SuppressedDisconnect of(ConnectionPhase phase,
                                          String loggerName,
                                          LostConnectionFormat format,
                                          Object[] parameters) {
        LostConnectionFormat shape = format == null ? LostConnectionFormat.IDENTIFIER_AND_REASON : format;
        String first = text(parameters, 0);
        String subject = switch (shape) {
            case NAME_UUID_AND_REASON -> first + " (" + text(parameters, 1) + ")";
            case IDENTIFIER_AND_REASON -> first;
        };
        String reason = text(parameters, shape.requiredParameterCount() - 1);
        return new SuppressedDisconnect(phase, loggerName, subject, reason);
    }

    /**
     * @return the disconnect line as Minecraft would have printed it
     */
    public String describe() {
        return subject + " lost connection: " + reason;
    }

    private static String text(Object[] parameters, int index) {
        if (parameters == null || index < 0 || index >= parameters.length) {
            return UNKNOWN;
        }
        Object value = parameters[index];
        return value == null ? UNKNOWN : String.valueOf(value);
    }
}

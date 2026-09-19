package com.cleanerlogs.common;

import org.apache.logging.log4j.message.Message;

/**
 * Helpers for reading Minecraft's parameterised "lost connection" messages without formatting them.
 */
public final class DisconnectMessages {

    /**
     * @param message a log message, may be {@code null}
     * @return the disconnect shape, or {@code null} when the message is not a Minecraft disconnect line
     */
    public static LostConnectionFormat formatOf(Message message) {
        return message == null ? null : LostConnectionFormat.fromFormat(message.getFormat());
    }

    /**
     * @param message a log message, may be {@code null}
     * @return the raw message parameters, or {@code null} when the message is not parameterised
     */
    public static Object[] parametersOf(Message message) {
        return message == null ? null : message.getParameters();
    }

    private DisconnectMessages() {
    }
}

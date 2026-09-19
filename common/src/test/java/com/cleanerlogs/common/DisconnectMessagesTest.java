package com.cleanerlogs.common;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DisconnectMessagesTest {

    @Test
    @DisplayName("recognises the two Minecraft disconnect shapes")
    void recognisesKnownShapes() {
        assertAll(
            () -> assertEquals(LostConnectionFormat.IDENTIFIER_AND_REASON,
                DisconnectMessages.formatOf(new ParameterizedMessage("{} lost connection: {}", "a", "b"))),
            () -> assertEquals(LostConnectionFormat.NAME_UUID_AND_REASON,
                DisconnectMessages.formatOf(new ParameterizedMessage("{} ({}) lost connection: {}", "a", "b", "c"))));
    }

    @Test
    @DisplayName("tolerates the trailing text Paper appends in debug mode")
    void toleratesPaperDebugSuffix() {
        assertEquals(LostConnectionFormat.NAME_UUID_AND_REASON,
            DisconnectMessages.formatOf(new ParameterizedMessage(
                "{} ({}) lost connection: {}, while in configuration phase {}",
                "a", "b", "c", "d")));
    }

    @Test
    @DisplayName("reports how many parameters the shape requires")
    void reportsRequiredParameterCount() {
        assertAll(
            () -> assertEquals(2, LostConnectionFormat.IDENTIFIER_AND_REASON.requiredParameterCount()),
            () -> assertEquals(3, LostConnectionFormat.NAME_UUID_AND_REASON.requiredParameterCount()));
    }

    @Test
    @DisplayName("literal text is never mistaken for a disconnect line")
    void rejectsLiteralText() {
        assertAll(
            () -> assertNull(DisconnectMessages.formatOf(null)),
            () -> assertNull(DisconnectMessages.formatOf(new SimpleMessage("lost connection: Disconnected"))),
            () -> assertNull(DisconnectMessages.formatOf(
                new SimpleMessage("NecoConneco lost connection: Disconnected"))),
            () -> assertNull(DisconnectMessages.formatOf(
                new ParameterizedMessage("Disconnecting {}: {}", "a", "b"))),
            () -> assertNull(DisconnectMessages.formatOf(
                new ParameterizedMessage("{} lost connection but recovered: {}", "a", "b"))));
    }

    @Test
    @DisplayName("parameters are exposed without formatting the message")
    void exposesParameters() {
        ParameterizedMessage message = new ParameterizedMessage("{} lost connection: {}", "identifier", "reason");

        assertEquals(2, DisconnectMessages.parametersOf(message).length);
        assertNull(DisconnectMessages.parametersOf(null));
    }
}

package com.cleanerlogs.common;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PrePlayIdentifierMatcherTest {

    @ParameterizedTest(name = "pre-play identifier {0}")
    @ValueSource(strings = {
        "Probe (/32.188.152.209:51894)",
        "ServerListBot (/81.88.19.183:33182)",
        "(/32.188.152.209:51894)",
        "/32.188.152.209:51894",
        "Probe (/[2001:db8::1]:51894)",
        "Probe (/0:0:0:0:0:0:0:1:51894)",
        "Probe (/localhost:25565)",
        "Probe (/localhost/127.0.0.1:25565)",
        "Probe (/play.example.com:25565)",
        "Probe (/sub.play.example.com:1)",
        "com.mojang.authlib.GameProfile@1f2e[id=<null>,name=Probe,properties={},legacy=false]",
        "com.mojang.authlib.GameProfile@1f2e[id=<null>,name=Probe,properties={},legacy=false] (/192.168.1.12:51901)",
        "GameProfile@1f2e[id=abc,name=Probe]",
    })
    @DisplayName("recognises identifiers only a pre-join connection can produce")
    void recognisesPrePlayIdentifiers(String identifier) {
        assertTrue(PrePlayIdentifierMatcher.isPrePlayIdentifier(identifier), identifier);
    }

    @ParameterizedTest(name = "player name {0}")
    @ValueSource(strings = {
        "NecoConneco",
        "Player",
        "notch",
        "a",
        "_",
        "__x__",
        "Player_1",
        "1234567890123456",
        "Obs_probe",
        "ver1111",
        "Email",
        "ServerListBot",
    })
    @DisplayName("never treats a plain player name as a pre-join identifier")
    void rejectsPlayerNames(String name) {
        assertFalse(PrePlayIdentifierMatcher.isPrePlayIdentifier(name), name);
    }

    @Test
    @DisplayName("blank and null identifiers are rejected")
    void rejectsBlankIdentifiers() {
        assertAll(
            () -> assertFalse(PrePlayIdentifierMatcher.isPrePlayIdentifier(null)),
            () -> assertFalse(PrePlayIdentifierMatcher.isPrePlayIdentifier("")),
            () -> assertFalse(PrePlayIdentifierMatcher.isPrePlayIdentifier("   ")),
            () -> assertFalse(PrePlayIdentifierMatcher.isPrePlayIdentifier("(/)")));
    }

    @Test
    @DisplayName("an address must be complete to count")
    void rejectsIncompleteAddresses() {
        assertAll(
            () -> assertFalse(PrePlayIdentifierMatcher.isPrePlayIdentifier("Probe (/32.188.152.209)")),
            () -> assertFalse(PrePlayIdentifierMatcher.isPrePlayIdentifier("Probe (32.188.152.209)")),
            () -> assertFalse(PrePlayIdentifierMatcher.isPrePlayIdentifier("Probe (/32.188.152.209:)")),
            () -> assertFalse(PrePlayIdentifierMatcher.isPrePlayIdentifier("32.188.152.209:51894")));
    }

    @Test
    @DisplayName("recognises game profile UUIDs")
    void recognisesUuids() {
        assertAll(
            () -> assertTrue(PrePlayIdentifierMatcher.isUuid("069a79f4-44e9-4726-a5be-fca90e38aaf5")),
            () -> assertTrue(PrePlayIdentifierMatcher.isUuid(" 069A79F4-44E9-4726-A5BE-FCA90E38AAF5 ")),
            () -> assertFalse(PrePlayIdentifierMatcher.isUuid(null)),
            () -> assertFalse(PrePlayIdentifierMatcher.isUuid("not-a-uuid")),
            () -> assertFalse(PrePlayIdentifierMatcher.isUuid("069a79f444e94726a5befca90e38aaf5")));
    }
}

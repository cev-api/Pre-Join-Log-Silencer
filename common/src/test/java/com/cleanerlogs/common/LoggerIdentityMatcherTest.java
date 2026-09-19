package com.cleanerlogs.common;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoggerIdentityMatcherTest {

    private final LoggerIdentityMatcher matcher = MinecraftLoggerNames.defaultMatcher();

    @Test
    @DisplayName("classifies the Mojang-mapped listener classes")
    void classifiesNamedListenerClasses() {
        assertAll(
            () -> assertEquals(ConnectionPhase.LOGIN, matcher.classify(MinecraftLoggerNames.LOGIN_LISTENER_CLASS)),
            () -> assertEquals(ConnectionPhase.CONFIGURATION,
                matcher.classify(MinecraftLoggerNames.CONFIGURATION_LISTENER_CLASS)),
            () -> assertEquals(ConnectionPhase.PLAY, matcher.classify(MinecraftLoggerNames.PLAY_LISTENER_CLASS)));
    }

    @Test
    @DisplayName("classifies by simple name when only the package differs")
    void classifiesBySimpleName() {
        assertAll(
            () -> assertEquals(ConnectionPhase.LOGIN, matcher.classify("com.example.ServerLoginPacketListenerImpl")),
            () -> assertEquals(ConnectionPhase.CONFIGURATION,
                matcher.classify("ServerConfigurationPacketListenerImpl")),
            () -> assertEquals(ConnectionPhase.PLAY, matcher.classify("some.pkg.ServerGamePacketListenerImpl")));
    }

    @Test
    @DisplayName("simple names only match at a package or class boundary")
    void simpleNamesMatchOnlyAtBoundaries() {
        assertAll(
            () -> assertEquals(ConnectionPhase.LOGIN, matcher.classify("net.minecraft.server.LoginListener")),
            () -> assertEquals(ConnectionPhase.UNKNOWN, matcher.classify("net.minecraft.future.RenamedLoginListener")),
            () -> assertEquals(ConnectionPhase.UNKNOWN,
                matcher.classify("com.example.MyServerLoginPacketListenerImplWrapper")),
            () -> assertEquals(ConnectionPhase.LOGIN,
                matcher.classify("com.example.ServerLoginPacketListenerImpl$Inner")));
    }

    @Test
    @DisplayName("classifies the versioned Spigot package layout")
    void classifiesSpigotStylePackage() {
        assertEquals(ConnectionPhase.LOGIN,
            matcher.classify("net.minecraft.server.v1_20_R1.network.ServerLoginPacketListenerImpl"));
    }

    @Test
    @DisplayName("classification is case insensitive and tolerant of nested class suffixes")
    void normalisesUnusualNames() {
        assertAll(
            () -> assertEquals(ConnectionPhase.LOGIN,
                matcher.classify("net.minecraft.server.network.serverloginpacketlistenerimpl")),
            () -> assertEquals(ConnectionPhase.LOGIN,
                matcher.classify("net.minecraft.server.network.ServerLoginPacketListenerImpl$1")),
            () -> assertEquals(ConnectionPhase.PLAY,
                matcher.classify("NET.MINECRAFT.SERVER.NETWORK.SERVERGAMEPACKETLISTENERIMPL")));
    }

    @Test
    @DisplayName("PLAY is evaluated before the pre-join phases")
    void playWinsOverPreJoin() {
        LoggerIdentityMatcher biased = LoggerIdentityMatcher.builder()
            .addExactName("example.Listener", ConnectionPhase.PLAY)
            .addExactName("example.Listener", ConnectionPhase.LOGIN)
            .build();

        assertEquals(ConnectionPhase.PLAY, biased.classify("example.Listener"));
    }

    @Test
    @DisplayName("the shared common listener is deliberately left unknown")
    void sharedListenerIsUnknown() {
        assertEquals(ConnectionPhase.UNKNOWN,
            matcher.classify(MinecraftLoggerNames.LOGIN_LISTENER_CLASS.replace(
                "ServerLoginPacketListenerImpl", MinecraftLoggerNames.SHARED_LISTENER_SIMPLE_NAME)));
    }

    @Test
    @DisplayName("unknown and malformed names are unknown, never exceptions")
    void handlesUnknownNames() {
        assertAll(
            () -> assertEquals(ConnectionPhase.UNKNOWN, matcher.classify(null)),
            () -> assertEquals(ConnectionPhase.UNKNOWN, matcher.classify("")),
            () -> assertEquals(ConnectionPhase.UNKNOWN, matcher.classify("net.minecraft.class_3242")),
            () -> assertEquals(ConnectionPhase.UNKNOWN, matcher.classify("org.bukkit.SomePlugin")),
            () -> assertEquals(ConnectionPhase.UNKNOWN, matcher.classify("$")));
    }

    @Test
    @DisplayName("the legacy CraftBukkit login handler is recognised")
    void recognisesLegacyLoginHandler() {
        assertEquals(ConnectionPhase.LOGIN, matcher.classify("net.minecraft.server.LoginListener"));
    }

    @Test
    @DisplayName("registered ignored names are excluded")
    void honoursIgnoredNames() {
        assertAll(
            () -> assertTrue(matcher.isIgnored("PreJoinLogSilencer")),
            () -> assertFalse(matcher.isIgnored(MinecraftLoggerNames.LOGIN_LISTENER_CLASS)),
            () -> assertFalse(matcher.isIgnored(null)));
    }

    @Test
    @DisplayName("platform adapters can register resolved runtime names")
    void acceptsPlatformSpecificAliases() {
        // This mirrors what the Fabric adapter does with the loader's mapping resolver.
        LoggerIdentityMatcher extended = MinecraftLoggerNames.defaultMatcherBuilder()
            .addExactName("net.minecraft.class_3242", ConnectionPhase.LOGIN)
            .addExactName("net.minecraft.class_3244", ConnectionPhase.PLAY)
            .build();

        assertAll(
            () -> assertEquals(ConnectionPhase.LOGIN, extended.classify("net.minecraft.class_3242")),
            () -> assertEquals(ConnectionPhase.PLAY, extended.classify("net.minecraft.class_3244")),
            () -> assertEquals(ConnectionPhase.LOGIN, extended.classify(MinecraftLoggerNames.LOGIN_LISTENER_CLASS)));
    }

    @Test
    @DisplayName("the registered tables are exposed for diagnostics and never null")
    void exposesRegisteredTables() {
        assertAll(
            () -> assertTrue(matcher.exactNames(ConnectionPhase.LOGIN)
                .contains(MinecraftLoggerNames.LOGIN_LISTENER_CLASS)),
            () -> assertTrue(matcher.simpleNames(ConnectionPhase.LOGIN)
                .contains(MinecraftLoggerNames.LOGIN_LISTENER_SIMPLE_NAME)),
            () -> assertFalse(matcher.exactNames(ConnectionPhase.UNKNOWN).iterator().hasNext()),
            () -> assertTrue(matcher.ignoredNames().contains("PreJoinLogSilencer")));
    }
}

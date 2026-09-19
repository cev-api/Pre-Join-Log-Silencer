package com.cleanerlogs.common;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.cleanerlogs.common.log4j.Log4jDetectionDiagnostics;

/**
 * The detector is the safety critical part of this project, so the tests are deliberately paranoid:
 * every shape that must be suppressed, every shape that must survive, and every shape that a future Minecraft
 * version could introduce.
 */
class PreJoinDisconnectDetectorTest {

    private static final String DISCONNECTED = "Disconnected";

    private final PreJoinDisconnectDetector detector =
        new PreJoinDisconnectDetector(MinecraftLoggerNames.defaultMatcher(), DetectionDiagnostics.NOOP);

    // ------------------------------------------------------------------
    // PRE-PLAY messages that must be suppressed
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "login identifier {0}")
    @ValueSource(strings = {
        "Obs_probe (/32.188.152.209:51894)",
        "ver1111 (/46.147.84.109:52727)",
        "Email (/46.147.84.109:60942)",
        "ServerListBot (/81.88.19.183:33182)",
    })
    @DisplayName("suppresses the login-phase disconnect lines from the report")
    void suppressesReportedLoginPhaseDisconnects(String identifier) {
        LogEvent event = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.lostConnection(identifier, DISCONNECTED));

        assertAll(
            () -> assertTrue(detector.shouldSuppress(event), identifier),
            () -> assertEquals(ConnectionPhase.LOGIN, detector.classify(event).phase()),
            () -> assertEquals(DetectionBasis.LOGGER_IDENTITY, detector.classify(event).basis()));
    }

    @Test
    @DisplayName("suppresses the game-profile form the login listener produces before the name is resolved")
    void suppressesGameProfileStyleLoginDisconnect() {
        LogEvent event = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.lostConnection(
                LogEventFixtures.GAME_PROFILE_TO_STRING + " (/192.168.1.12:51901)",
                "Authentication servers are down"));

        assertTrue(detector.shouldSuppress(event));
    }

    @Test
    @DisplayName("suppresses the bare remote address form used before any profile exists")
    void suppressesBareAddressLoginDisconnect() {
        LogEvent event = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.lostConnection("/192.168.1.12:51901", DISCONNECTED));

        assertTrue(detector.shouldSuppress(event));
    }

    @Test
    @DisplayName("suppresses configuration-phase disconnects that carry a game profile UUID")
    void suppressesConfigurationPhaseDisconnect() {
        LogEvent event = LogEventFixtures.info(LogEventFixtures.CONFIGURATION_LOGGER,
            LogEventFixtures.lostConnectionWithUuid("NecoConneco", LogEventFixtures.SAMPLE_UUID, DISCONNECTED));

        PreJoinClassification classification = detector.classify(event);

        assertAll(
            () -> assertTrue(classification.suppressible()),
            () -> assertEquals(ConnectionPhase.CONFIGURATION, classification.phase()),
            () -> assertEquals(DetectionBasis.LOGGER_IDENTITY, classification.basis()));
    }

    @ParameterizedTest(name = "identifier {0}")
    @ValueSource(strings = {
        "Probe (/32.188.152.209:51894)",
        "Probe (/46.147.84.109:52727)",
        "Probe (/[2001:db8::1]:51894)",
        "Probe (/0:0:0:0:0:0:0:1:51894)",
        "Probe (/localhost:25565)",
        "Probe (/localhost/127.0.0.1:25565)",
        "Probe (/192.168.1.12:25565)",
        "Probe (/[fe80::1%eth0]:25565)",
    })
    @DisplayName("suppresses every remote address form of the login identifier")
    void suppressesAllRemoteAddressForms(String identifier) {
        LogEvent event = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.lostConnection(identifier, "Timed out"));

        assertTrue(detector.shouldSuppress(event), identifier);
    }

    // ------------------------------------------------------------------
    // Unknown logger identities: conservative message structure fallback
    // ------------------------------------------------------------------

    @Test
    @DisplayName("falls back to the message structure for an unmapped Minecraft logger")
    void suppressesThroughMessageStructureForIntermediaryLogger() {
        LogEvent event = LogEventFixtures.info(LogEventFixtures.INTERMEDIARY_LOGGER,
            LogEventFixtures.lostConnection("Probe (/32.188.152.209:51894)", DISCONNECTED));

        PreJoinClassification classification = detector.classify(event);

        assertAll(
            () -> assertTrue(classification.suppressible()),
            () -> assertEquals(ConnectionPhase.LOGIN, classification.phase()),
            () -> assertEquals(DetectionBasis.MESSAGE_STRUCTURE, classification.basis()));
    }

    @Test
    @DisplayName("falls back to the message structure for an unmapped configuration logger")
    void suppressesConfigurationShapeThroughMessageStructure() {
        LogEvent event = LogEventFixtures.info(LogEventFixtures.INTERMEDIARY_LOGGER,
            LogEventFixtures.lostConnectionWithUuid("NecoConneco", LogEventFixtures.SAMPLE_UUID, DISCONNECTED));

        PreJoinClassification classification = detector.classify(event);

        assertAll(
            () -> assertTrue(classification.suppressible()),
            () -> assertEquals(ConnectionPhase.CONFIGURATION, classification.phase()),
            () -> assertEquals(DetectionBasis.MESSAGE_STRUCTURE, classification.basis()));
    }

    @Test
    @DisplayName("never suppresses a name-only disconnect line coming from an unknown logger")
    void doesNotSuppressNameOnlyDisconnectFromUnknownLogger() {
        LogEvent event = LogEventFixtures.info(LogEventFixtures.INTERMEDIARY_LOGGER,
            LogEventFixtures.lostConnection("NecoConneco", DISCONNECTED));

        assertFalse(detector.shouldSuppress(event));
    }

    @Test
    @DisplayName("never suppresses a disconnect-shaped message from a third-party logger")
    void doesNotSuppressThirdPartyLoggerEvenWithRemoteAddress() {
        LogEvent event = LogEventFixtures.info(LogEventFixtures.THIRD_PARTY_LOGGER,
            LogEventFixtures.lostConnection("Probe (/32.188.152.209:51894)", DISCONNECTED));

        assertFalse(detector.shouldSuppress(event));
    }

    @Test
    @DisplayName("never suppresses the configuration shape when the second parameter is not a UUID")
    void doesNotSuppressConfigurationShapeWithoutUuid() {
        LogEvent event = LogEventFixtures.info(LogEventFixtures.INTERMEDIARY_LOGGER,
            LogEventFixtures.lostConnectionWithUuid("NecoConneco", "not-a-uuid", DISCONNECTED));

        assertFalse(detector.shouldSuppress(event));
    }

    @Test
    @DisplayName("leaves the shared listener unclassified so it can never hide a player disconnect")
    void sharedListenerIsNotClassifiedAsPrePlay() {
        LogEvent nameOnly = LogEventFixtures.info(LogEventFixtures.SHARED_LOGGER,
            LogEventFixtures.lostConnection("NecoConneco", DISCONNECTED));
        LogEvent withAddress = LogEventFixtures.info(LogEventFixtures.SHARED_LOGGER,
            LogEventFixtures.lostConnection("NecoConneco (/1.2.3.4:5)", DISCONNECTED));

        assertAll(
            () -> assertEquals(ConnectionPhase.UNKNOWN,
                detector.classify(LogEventFixtures.info(LogEventFixtures.SHARED_LOGGER,
                    LogEventFixtures.literal("anything"))).phase()),
            () -> assertFalse(detector.shouldSuppress(nameOnly)),
            // The game listener shape is a bare player name, so this is the only shape that survives.
            () -> assertTrue(detector.shouldSuppress(withAddress)));
    }

    // ------------------------------------------------------------------
    // PLAY messages that must never be suppressed
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "play disconnect {0}")
    @ValueSource(strings = {"Disconnected", "Timed out",
        "Internal Exception: io.netty.handler.codec.DecoderException"})
    @DisplayName("never suppresses a disconnect from a connection that reached PLAY")
    void neverSuppressesPlayPhaseDisconnects(String reason) {
        LogEvent event = LogEventFixtures.info(LogEventFixtures.PLAY_LOGGER,
            LogEventFixtures.lostConnection("NecoConneco", reason));

        PreJoinClassification classification = detector.classify(event);

        assertAll(
            () -> assertFalse(classification.suppressible()),
            () -> assertEquals(ConnectionPhase.PLAY, classification.phase()),
            () -> assertFalse(detector.shouldSuppress(event)));
    }

    @Test
    @DisplayName("a PLAY logger identity is a hard veto for every possible message")
    void playPhaseIsNeverSuppressibleWhateverTheMessage() {
        List<Message> messages = List.of(
            LogEventFixtures.lostConnection("NecoConneco", DISCONNECTED),
            LogEventFixtures.lostConnection("NecoConneco (/1.2.3.4:5)", DISCONNECTED),
            LogEventFixtures.lostConnection("/1.2.3.4:5", DISCONNECTED),
            LogEventFixtures.lostConnection(LogEventFixtures.GAME_PROFILE_TO_STRING, DISCONNECTED),
            LogEventFixtures.lostConnectionWithUuid("NecoConneco", LogEventFixtures.SAMPLE_UUID, DISCONNECTED),
            LogEventFixtures.literal("NecoConneco lost connection: Disconnected"),
            LogEventFixtures.objectMessage());

        assertAll(messages.stream().map(message -> () -> {
            LogEvent event = LogEventFixtures.info(LogEventFixtures.PLAY_LOGGER, message);
            assertFalse(detector.shouldSuppress(event), "PLAY event escaped the veto: " + message);
        }));
    }

    // ------------------------------------------------------------------
    // Unrelated messages that must never be suppressed
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "unrelated literal \"{0}\"")
    @ValueSource(strings = {
        "Authentication servers are down",
        "Failed to handle packet",
        "handleDisconnection() called twice",
        "DecoderException",
        "UUID of player Example is 069a79f4-44e9-4726-a5be-fca90e38aaf5",
        "Example joined the game",
        "Example left the game",
    })
    @DisplayName("never suppresses unrelated INFO messages from a pre-join logger")
    void neverSuppressesUnrelatedMessagesOnPreJoinLoggers(String text) {
        LogEvent loginEvent = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER, LogEventFixtures.literal(text));
        LogEvent configurationEvent =
            LogEventFixtures.info(LogEventFixtures.CONFIGURATION_LOGGER, LogEventFixtures.literal(text));

        assertAll(
            () -> assertFalse(detector.shouldSuppress(loginEvent), text),
            () -> assertFalse(detector.shouldSuppress(configurationEvent), text));
    }

    @Test
    @DisplayName("a literal message that merely contains the words lost connection is never suppressed")
    void neverSuppressesLiteralLostConnectionText() {
        // Same text a server would print, but produced without placeholders, so the format string carries no
        // '{}' markers and cannot be confused with the real disconnect logging call.
        LogEvent literal = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.literal("Probe (/32.188.152.209:51894) lost connection: Disconnected"));
        LogEvent formatted = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.formatted("Probe (/32.188.152.209:51894) lost connection: Disconnected"));

        assertAll(
            () -> assertFalse(detector.shouldSuppress(literal)),
            () -> assertFalse(detector.shouldSuppress(formatted)));
    }

    @Test
    @DisplayName("suppresses only the disconnect call of a pre-join logger, not its other logging")
    void suppressesOnlyTheDisconnectCall() {
        LogEvent otherCall = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            new ParameterizedMessage("Disconnecting {}: {}", "Probe", "Kicked"));
        LogEvent disconnect = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", DISCONNECTED));

        assertAll(
            () -> assertFalse(detector.shouldSuppress(otherCall)),
            () -> assertTrue(detector.shouldSuppress(disconnect)));
    }

    @ParameterizedTest(name = "level {0}")
    @ValueSource(strings = {"WARN", "ERROR", "FATAL", "DEBUG", "TRACE"})
    @DisplayName("only INFO events are ever considered")
    void onlyInfoEventsAreConsidered(String levelName) {
        Level level = Level.getLevel(levelName);
        LogEvent event = LogEventFixtures.event(LogEventFixtures.LOGIN_LOGGER, level,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", DISCONNECTED));

        assertFalse(detector.shouldSuppress(event), levelName);
    }

    @Test
    @DisplayName("an INFO event carrying a throwable is not a plain disconnect line")
    void throwableBearingEventsAreNotSuppressed() {
        LogEvent event = LogEventFixtures.infoWithThrowable(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", DISCONNECTED));

        assertFalse(detector.shouldSuppress(event));
    }

    // ------------------------------------------------------------------
    // Edge cases and future versions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("null and unusual events never throw and are never suppressed")
    void handlesMalformedEvents() {
        LogEvent noLoggerName = LogEventFixtures.event(null, Level.INFO,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", DISCONNECTED));
        LogEvent emptyLoggerName = LogEventFixtures.event("", Level.INFO,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", DISCONNECTED));
        LogEvent noMessage = LogEventFixtures.event(LogEventFixtures.LOGIN_LOGGER, Level.INFO, null);
        LogEvent nullParameters = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            new ParameterizedMessage(LostConnectionFormat.IDENTIFIER_AND_REASON.prefix(), (Object[]) null));

        assertAll(
            () -> assertFalse(detector.shouldSuppress(null)),
            () -> assertEquals(ConnectionPhase.UNKNOWN, detector.classify(null).phase()),
            () -> assertFalse(detector.shouldSuppress(noLoggerName)),
            () -> assertFalse(detector.shouldSuppress(emptyLoggerName)),
            () -> assertFalse(detector.shouldSuppress(noMessage)),
            // The format alone is not enough: the parameters the real logging call always adds must be there.
            () -> assertFalse(detector.shouldSuppress(nullParameters)));
    }

    @ParameterizedTest(name = "null identifier for {0}")
    @CsvSource(nullValues = "NULL", value = {
        "NULL, Disconnected",
        "'', Disconnected",
        "'   ', Disconnected",
    })
    @DisplayName("blank or null identifiers are not treated as pre-join identifiers")
    void blankIdentifiersAreNotPrePlay(String identifier, String reason) {
        LogEvent event = LogEventFixtures.info(LogEventFixtures.INTERMEDIARY_LOGGER,
            LogEventFixtures.lostConnection(identifier, reason));

        assertFalse(detector.shouldSuppress(event));
    }

    @ParameterizedTest(name = "unknown logger {0}")
    @ValueSource(strings = {
        "net.minecraft.server.network.SomeFuturePacketListenerImpl",
        "net.minecraft.future.RenamedLoginListener",
        "net.minecraft.class_99999",
        "org.bukkit.SomePlugin",
        "io.papermc.paper.adventure.Something",
    })
    @DisplayName("unknown logger categories are left alone unless the message shape is unmistakable")
    void unknownLoggersAreLeftAlone(String loggerName) {
        LogEvent nameOnly = LogEventFixtures.info(loggerName, LogEventFixtures.lostConnection("NecoConneco", DISCONNECTED));

        assertFalse(detector.shouldSuppress(nameOnly), loggerName);
    }

    @Test
    @DisplayName("future Minecraft loggers still benefit from the conservative fallback")
    void futureMinecraftLoggersStillGetTheFallback() {
        LogEvent event = LogEventFixtures.info("net.minecraft.server.network.SomeFutureLoginListener",
            LogEventFixtures.lostConnection("Probe (/32.188.152.209:51894)", DISCONNECTED));

        assertTrue(detector.shouldSuppress(event));
    }

    @Test
    @DisplayName("custom disconnect reasons do not change the verdict")
    void customDisconnectReasonsAreHandled() {
        LogEvent preJoin = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", "You are not whitelisted on this server!"));
        LogEvent play = LogEventFixtures.info(LogEventFixtures.PLAY_LOGGER,
            LogEventFixtures.lostConnection("NecoConneco", "You are not whitelisted on this server!"));

        assertAll(
            () -> assertTrue(detector.shouldSuppress(preJoin)),
            () -> assertFalse(detector.shouldSuppress(play)));
    }

    @Test
    @DisplayName("this project's own diagnostic logger is never classified")
    void diagnosticLoggerIsIgnored() {
        LogEvent event = LogEventFixtures.info(Log4jDetectionDiagnostics.LOGGER_NAME,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", DISCONNECTED));

        assertFalse(detector.shouldSuppress(event));
    }

    @Test
    @DisplayName("classification is safe to use from many threads at once")
    void classificationIsThreadSafe() {
        LogEvent preJoin = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", DISCONNECTED));
        LogEvent play = LogEventFixtures.info(LogEventFixtures.PLAY_LOGGER,
            LogEventFixtures.lostConnection("NecoConneco", DISCONNECTED));

        boolean consistent = IntStream.range(0, 8).parallel().allMatch(index ->
            IntStream.range(0, 4_000).allMatch(iteration ->
                detector.shouldSuppress(preJoin) && !detector.shouldSuppress(play)));

        assertTrue(consistent);
    }
}

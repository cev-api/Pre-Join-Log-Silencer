package com.cleanerlogs.common.log4j;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.cleanerlogs.common.ConnectionPhase;
import com.cleanerlogs.common.LogEventFixtures;
import com.cleanerlogs.common.LoggerIdentityMatcher;
import com.cleanerlogs.common.MinecraftLoggerNames;
import com.cleanerlogs.common.PreJoinDisconnectDetector;
import com.cleanerlogs.common.SuppressedDisconnect;
import com.cleanerlogs.common.SuppressionListener;
import com.cleanerlogs.common.SuppressionPolicy;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreJoinDisconnectFilterTest {

    private final List<SuppressedDisconnect> suppressed = new ArrayList<>();

    private final AtomicReference<SuppressionPolicy> policy =
        LogEventFixtures.policyRef(SuppressionPolicy.defaults());

    private final SuppressionListener listener = suppressed::add;

    @Test
    @DisplayName("denies pre-join disconnects and reports a usable snapshot to the listener")
    void deniesPreJoinDisconnects() {
        PreJoinDisconnectFilter filter = filter(listener);

        assertEquals(Filter.Result.DENY, filter.filter(loginEvent()));

        assertEquals(1, suppressed.size());
        SuppressedDisconnect snapshot = suppressed.get(0);
        assertAll(
            () -> assertEquals(ConnectionPhase.LOGIN, snapshot.phase()),
            () -> assertEquals(LogEventFixtures.LOGIN_LOGGER, snapshot.loggerName()),
            () -> assertEquals("Probe (/1.2.3.4:5)", snapshot.subject()),
            () -> assertEquals("Disconnected", snapshot.reason()),
            () -> assertEquals("Probe (/1.2.3.4:5) lost connection: Disconnected", snapshot.describe()));
    }

    @Test
    @DisplayName("rebuilds the configuration phase shape with the profile UUID")
    void reportsConfigurationShape() {
        PreJoinDisconnectFilter filter = filter(listener);

        assertEquals(Filter.Result.DENY, filter.filter(configurationEvent()));

        assertEquals("NecoConneco (" + LogEventFixtures.SAMPLE_UUID + ") lost connection: Disconnected",
            suppressed.get(0).describe());
    }

    @Test
    @DisplayName("never denies a PLAY disconnect, whatever the settings are")
    void neverDeniesPlayDisconnects() {
        PreJoinDisconnectFilter filter = filter(listener);

        assertAll(
            () -> assertEquals(Filter.Result.NEUTRAL, filter.filter(playEvent())),
            () -> assertTrue(suppressed.isEmpty()));
    }

    @Test
    @DisplayName("returns NEUTRAL for anything that is not a pre-join disconnect")
    void returnsNeutralForEverythingElse() {
        PreJoinDisconnectFilter filter = filter(listener);

        LogEvent unrelated = LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.literal("Authentication servers are down"));
        LogEvent warning = LogEventFixtures.event(LogEventFixtures.LOGIN_LOGGER, Level.WARN,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", "Disconnected"));

        assertAll(
            () -> assertEquals(Filter.Result.NEUTRAL, filter.filter(unrelated)),
            () -> assertEquals(Filter.Result.NEUTRAL, filter.filter(warning)),
            () -> assertEquals(Filter.Result.NEUTRAL, filter.filter((LogEvent) null)),
            () -> assertTrue(suppressed.isEmpty()));
    }

    @Test
    @DisplayName("honours a master switch that is turned off")
    void honoursDisabledPolicy() {
        PreJoinDisconnectFilter filter = filter(listener);
        policy.set(LogEventFixtures.policy(false, true, true));

        assertEquals(Filter.Result.NEUTRAL, filter.filter(loginEvent()));
        assertTrue(suppressed.isEmpty());
    }

    @Test
    @DisplayName("honours the per-phase switches independently")
    void honoursPerPhaseSwitches() {
        PreJoinDisconnectFilter filter = filter(listener);

        policy.set(LogEventFixtures.policy(true, false, true));
        assertEquals(Filter.Result.NEUTRAL, filter.filter(loginEvent()));
        assertEquals(Filter.Result.DENY, filter.filter(configurationEvent()));

        policy.set(LogEventFixtures.policy(true, true, false));
        assertEquals(Filter.Result.DENY, filter.filter(loginEvent()));
        assertEquals(Filter.Result.NEUTRAL, filter.filter(configurationEvent()));

        assertEquals(2, suppressed.size());
        assertAll(
            () -> assertEquals(ConnectionPhase.CONFIGURATION, suppressed.get(0).phase()),
            () -> assertEquals(ConnectionPhase.LOGIN, suppressed.get(1).phase()));
    }

    @Test
    @DisplayName("a deactivated filter is permanently inert")
    void deactivatedFilterDoesNothing() {
        PreJoinDisconnectFilter filter = filter(listener);

        filter.deactivate();

        assertAll(
            () -> assertEquals(Filter.Result.NEUTRAL, filter.filter(loginEvent())),
            () -> assertEquals(Filter.Result.NEUTRAL, filter.filter(configurationEvent())),
            () -> assertTrue(suppressed.isEmpty()));
    }

    @Test
    @DisplayName("a throwing listener never breaks logging")
    void throwingListenerIsSwallowed() {
        PreJoinDisconnectFilter filter = filter(value -> {
            throw new IllegalStateException("listener is broken");
        });

        assertEquals(Filter.Result.DENY, filter.filter(loginEvent()));
    }

    private PreJoinDisconnectFilter filter(SuppressionListener suppressionListener) {
        LoggerIdentityMatcher matcher = MinecraftLoggerNames.defaultMatcher();
        return PreJoinDisconnectFilter.create(
            new PreJoinDisconnectDetector(matcher, null), policy, suppressionListener);
    }

    private static LogEvent loginEvent() {
        return LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", "Disconnected"));
    }

    private static LogEvent configurationEvent() {
        return LogEventFixtures.info(LogEventFixtures.CONFIGURATION_LOGGER,
            LogEventFixtures.lostConnectionWithUuid("NecoConneco", LogEventFixtures.SAMPLE_UUID, "Disconnected"));
    }

    private static LogEvent playEvent() {
        return LogEventFixtures.info(LogEventFixtures.PLAY_LOGGER,
            LogEventFixtures.lostConnection("NecoConneco", "Disconnected"));
    }
}

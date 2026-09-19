package com.cleanerlogs.common.log4j;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cleanerlogs.common.LogEventFixtures;
import com.cleanerlogs.common.MinecraftLoggerNames;
import com.cleanerlogs.common.PreJoinDisconnectDetector;
import com.cleanerlogs.common.SuppressionListener;
import com.cleanerlogs.common.SuppressionPolicy;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.CompositeFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the installation contract: compose with whatever is already there, never destroy another plugin's
 * filter, remove only ours, and survive double enable and double disable.
 */
class Log4jFilterInstallerTest {

    @Test
    @DisplayName("installs on a configuration that has no filter yet")
    void installsOnAnEmptyConfiguration() {
        Configuration configuration = newTestConfiguration();
        PreJoinDisconnectFilter filter = filter();

        assertTrue(Log4jFilterInstaller.installOn(configuration, filter));
        assertTrue(Log4jFilterInstaller.containsFilter(
            configuration.getRootLogger().getFilter(), filter));
    }

    @Test
    @DisplayName("installing into an empty chain stores the filter directly")
    void installsIntoAnEmptyChain() {
        Configuration configuration = newTestConfiguration();
        PreJoinDisconnectFilter filter = filter();

        assertTrue(Log4jFilterInstaller.installOn(configuration, filter));
        assertSame(filter, configuration.getRootLogger().getFilter());
    }

    @Test
    @DisplayName("double install adds exactly one filter")
    void installIsIdempotent() {
        Configuration configuration = newTestConfiguration();
        Filter foreign = LogEventFixtures.neutralFilter();
        configuration.getRootLogger().addFilter(foreign);
        PreJoinDisconnectFilter filter = filter();

        assertAll(
            () -> assertTrue(Log4jFilterInstaller.installOn(configuration, filter)),
            () -> assertTrue(Log4jFilterInstaller.installOn(configuration, filter)));

        Filter chain = configuration.getRootLogger().getFilter();
        assertTrue(chain instanceof CompositeFilter);
        // Exactly the foreign filter plus ours: a second enable must not add a duplicate.
        assertEquals(2, ((CompositeFilter) chain).size());
        assertTrue(Log4jFilterInstaller.containsFilter(chain, filter));
        assertTrue(Log4jFilterInstaller.containsFilter(chain, foreign));
    }

    @Test
    @DisplayName("composes with an existing filter instead of replacing it")
    void installKeepsAnExistingFilter() {
        Configuration configuration = newTestConfiguration();
        Filter foreign = LogEventFixtures.neutralFilter();
        configuration.getRootLogger().addFilter(foreign);
        PreJoinDisconnectFilter filter = filter();

        assertTrue(Log4jFilterInstaller.installOn(configuration, filter));

        assertAll(
            () -> assertTrue(Log4jFilterInstaller.containsFilter(configuration.getRootLogger().getFilter(), foreign),
                "a filter installed by another plugin must survive installation"),
            () -> assertTrue(Log4jFilterInstaller.containsFilter(configuration.getRootLogger().getFilter(), filter)));
    }

    @Test
    @DisplayName("uninstall removes only our filter and restores the previous one")
    void uninstallRestoresThePreviousFilter() {
        Configuration configuration = newTestConfiguration();
        Filter foreign = LogEventFixtures.neutralFilter();
        configuration.getRootLogger().addFilter(foreign);
        PreJoinDisconnectFilter filter = filter();
        Log4jFilterInstaller.installOn(configuration, filter);

        assertTrue(Log4jFilterInstaller.uninstallFrom(configuration, filter));

        assertAll(
            () -> assertSame(foreign, configuration.getRootLogger().getFilter(),
                "the other filter must be unwrapped back into place"),
            () -> assertTrue(configuration.getRootLogger().hasFilter()),
            () -> assertFalse(Log4jFilterInstaller.containsFilter(
                configuration.getRootLogger().getFilter(), filter)));
    }

    @Test
    @DisplayName("uninstalling a filter that was never installed leaves the chain alone")
    void uninstallIsIdempotent() {
        Configuration configuration = newTestConfiguration();
        Filter foreign = LogEventFixtures.neutralFilter();
        configuration.getRootLogger().addFilter(foreign);

        assertAll(
            () -> assertTrue(Log4jFilterInstaller.uninstallFrom(configuration, filter())),
            () -> assertTrue(Log4jFilterInstaller.uninstallFrom(configuration, filter())),
            () -> assertSame(foreign, configuration.getRootLogger().getFilter()));
    }

    @Test
    @DisplayName("containsFilter understands plain and composite chains")
    void containsFilterUnderstandsBothChainShapes() {
        Filter first = LogEventFixtures.neutralFilter();
        Filter second = LogEventFixtures.neutralFilter();

        assertAll(
            () -> assertTrue(Log4jFilterInstaller.containsFilter(first, first)),
            () -> assertFalse(Log4jFilterInstaller.containsFilter(first, second)),
            () -> assertFalse(Log4jFilterInstaller.containsFilter(null, first)),
            () -> assertFalse(Log4jFilterInstaller.containsFilter(first, null)),
            () -> assertTrue(Log4jFilterInstaller.containsFilter(
                CompositeFilter.createFilters(new Filter[] {first, second}), second)));
    }

    @Test
    @DisplayName("end to end: only pre-join disconnects are blocked on a real logger config")
    void filtersThroughARealLoggerConfig() {
        Configuration configuration = newTestConfiguration();
        LoggerConfig root = configuration.getRootLogger();
        LogEventFixtures.RecordingAppender appender = new LogEventFixtures.RecordingAppender();
        root.addAppender(appender, Level.ALL, null);

        PreJoinDisconnectFilter filter = filter();
        assertTrue(Log4jFilterInstaller.installOn(configuration, filter));

        root.log(LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", "Disconnected")));
        root.log(LogEventFixtures.info(LogEventFixtures.PLAY_LOGGER,
            LogEventFixtures.lostConnection("NecoConneco", "Disconnected")));
        root.log(LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.literal("Authentication servers are down")));
        root.log(LogEventFixtures.info(LogEventFixtures.CONFIGURATION_LOGGER,
            LogEventFixtures.lostConnectionWithUuid("NecoConneco", LogEventFixtures.SAMPLE_UUID, "Disconnected")));

        assertEquals(2, appender.count(), "only the two non-pre-join events may reach the appender");

        assertTrue(Log4jFilterInstaller.uninstallFrom(configuration, filter));
        root.log(LogEventFixtures.info(LogEventFixtures.LOGIN_LOGGER,
            LogEventFixtures.lostConnection("Probe (/1.2.3.4:5)", "Disconnected")));

        assertEquals(3, appender.count(), "removal must restore the original logging behaviour");
    }

    @Test
    @DisplayName("installs on and removes from the live Log4j2 context")
    void installsOnTheLiveContext() {
        LoggerContext context = Log4jFilterInstaller.currentContext();
        assertNotNull(context, "the test runtime must run on Log4j2 core");
        LoggerConfig root = context.getConfiguration().getRootLogger();
        PreJoinDisconnectFilter filter = filter();

        try {
            assertTrue(Log4jFilterInstaller.install(filter));
            assertTrue(Log4jFilterInstaller.containsFilter(root.getFilter(), filter));
        } finally {
            assertTrue(Log4jFilterInstaller.uninstall(filter));
        }

        assertFalse(Log4jFilterInstaller.containsFilter(root.getFilter(), filter));
    }

    private static PreJoinDisconnectFilter filter() {
        return PreJoinDisconnectFilter.create(
            new PreJoinDisconnectDetector(MinecraftLoggerNames.defaultMatcher(), null),
            LogEventFixtures.policyRef(SuppressionPolicy.defaults()),
            SuppressionListener.NOOP);
    }

    private static Configuration newTestConfiguration() {
        Configuration configuration = new DefaultConfiguration();
        // DefaultConfiguration installs a console appender; drop it so a test only observes its own appender.
        LogEventFixtures.removeAllAppenders(configuration.getRootLogger());
        return configuration;
    }
}

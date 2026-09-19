package com.cleanerlogs.common;

import com.cleanerlogs.common.log4j.PreJoinDisconnectFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.message.StringFormattedMessage;

/**
 * Builders shared by the unit tests: realistic Log4j2 events without a running server.
 */
public final class LogEventFixtures {

    /** The logger the login listener uses in a Mojang-mapped runtime. */
    public static final String LOGIN_LOGGER = MinecraftLoggerNames.LOGIN_LISTENER_CLASS;

    /** The logger the configuration listener uses in a Mojang-mapped runtime. */
    public static final String CONFIGURATION_LOGGER = MinecraftLoggerNames.CONFIGURATION_LISTENER_CLASS;

    /** The logger the game listener uses in a Mojang-mapped runtime. */
    public static final String PLAY_LOGGER = MinecraftLoggerNames.PLAY_LISTENER_CLASS;

    /** A logger identity that this project must never classify. */
    public static final String SHARED_LOGGER = "net.minecraft.server.network.ServerCommonPacketListenerImpl";

    /** A plausible Fabric intermediary identity for the login listener. */
    public static final String INTERMEDIARY_LOGGER = "net.minecraft.class_3242";

    /** A logger that belongs to neither Minecraft nor this project. */
    public static final String THIRD_PARTY_LOGGER = "com.example.myplugin.ConnectionWatcher";

    /** A realistic offline-mode style profile UUID. */
    public static final String SAMPLE_UUID = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    /** A realistic game profile {@code toString()}, as produced while the username is not resolved yet. */
    public static final String GAME_PROFILE_TO_STRING =
        "com.mojang.authlib.GameProfile@1f2e3d4c[id=<null>,name=Probe,properties={},legacy=false]";

    private LogEventFixtures() {
    }

    /**
     * @param loggerName the Log4j2 category
     * @param level      the event level
     * @param message    the message
     * @return a Log4j2 event
     */
    public static LogEvent event(String loggerName, Level level, Message message) {
        return Log4jLogEvent.newBuilder()
            .setLoggerName(loggerName)
            .setLevel(level)
            .setMessage(message)
            .build();
    }

    /**
     * @param loggerName the Log4j2 category
     * @param message    the message
     * @return an INFO event
     */
    public static LogEvent info(String loggerName, Message message) {
        return event(loggerName, Level.INFO, message);
    }

    /**
     * @param loggerName the Log4j2 category
     * @param message    the message
     * @return an INFO event carrying a throwable
     */
    public static LogEvent infoWithThrowable(String loggerName, Message message) {
        return Log4jLogEvent.newBuilder()
            .setLoggerName(loggerName)
            .setLevel(Level.INFO)
            .setMessage(message)
            .setThrown(new IllegalStateException("boom"))
            .build();
    }

    /**
     * @param identifier the first parameter, an identifier or a player name
     * @param reason     the disconnect reason
     * @return {@code "{} lost connection: {}"}
     */
    public static Message lostConnection(String identifier, String reason) {
        return new ParameterizedMessage(LostConnectionFormat.IDENTIFIER_AND_REASON.prefix(), identifier, reason);
    }

    /**
     * @param name   the player name
     * @param uuid   the game profile UUID
     * @param reason the disconnect reason
     * @return {@code "{} ({}) lost connection: {}"}
     */
    public static Message lostConnectionWithUuid(String name, String uuid, String reason) {
        return new ParameterizedMessage(LostConnectionFormat.NAME_UUID_AND_REASON.prefix(), name, uuid, reason);
    }

    /**
     * @param text literal text without placeholders
     * @return a message that can never be matched as a disconnect line
     */
    public static Message literal(String text) {
        return new SimpleMessage(text);
    }

    /**
     * @param text literal text with {@code %s} style placeholders already substituted by the caller
     * @return a message whose format is the whole text
     */
    public static Message formatted(String text) {
        return new StringFormattedMessage(text);
    }

    /**
     * @return a message type that is not parameterised at all, so its format string is just the rendered text
     */
    public static Message objectMessage() {
        return new ObjectMessage(new StringBuilder("lost connection: Disconnected"));
    }

    /**
     * @param matcher    the identity table
     * @param policy     the live policy reference
     * @param suppressed collects the removed disconnect lines
     * @return a filter wired exactly the way the runtime wires it
     */
    public static PreJoinDisconnectFilter filter(LoggerIdentityMatcher matcher,
                                                 AtomicReference<SuppressionPolicy> policy,
                                                 List<SuppressedDisconnect> suppressed) {
        PreJoinDisconnectDetector detector = new PreJoinDisconnectDetector(matcher, null);
        return PreJoinDisconnectFilter.create(detector, policy,
            value -> Objects.requireNonNull(suppressed).add(value));
    }

    /**
     * @param policy the policy to wrap
     * @return a mutable reference holding the policy
     */
    public static AtomicReference<SuppressionPolicy> policyRef(SuppressionPolicy policy) {
        return new AtomicReference<>(policy);
    }

    /**
     * Builds a policy with the two optional outputs turned off.
     *
     * @param enabled       master switch
     * @param login         suppress login disconnects
     * @param configuration suppress configuration disconnects
     * @return the policy
     */
    public static SuppressionPolicy policy(boolean enabled, boolean login, boolean configuration) {
        return new SuppressionPolicy(enabled, login, configuration, false, false, false);
    }

    /**
     * @param login         suppress login disconnects
     * @param configuration suppress configuration disconnects
     * @return an enabled policy that logs neither to the console nor to a file
     */
    public static SuppressionPolicy policy(boolean login, boolean configuration) {
        return policy(true, login, configuration);
    }

    /**
     * @return a filter that never matches, used as a foreign filter that must survive installation
     */
    public static Filter neutralFilter() {
        return new AbstractFilter(Filter.Result.NEUTRAL, Filter.Result.NEUTRAL) {
            private static final long serialVersionUID = 1L;
        };
    }

    /**
     * Removes every appender the configuration happens to define, so a test only observes its own appender.
     *
     * @param loggerConfig the logger configuration to clear
     */
    public static void removeAllAppenders(LoggerConfig loggerConfig) {
        for (String name : new ArrayList<>(loggerConfig.getAppenders().keySet())) {
            loggerConfig.removeAppender(name);
        }
    }

    /**
     * An appender that records the events it receives.
     */
    public static final class RecordingAppender extends AbstractAppender {

        private final List<LogEvent> captured = new CopyOnWriteArrayList<>();

        /**
         * Creates a started appender.
         */
        public RecordingAppender() {
            super("recording", null, null, true, Property.EMPTY_ARRAY);
            start();
        }

        @Override
        public void append(LogEvent event) {
            captured.add(event.toImmutable());
        }

        /**
         * @return the recorded events
         */
        public List<LogEvent> captured() {
            return captured;
        }

        /**
         * @return the number of recorded events
         */
        public int count() {
            return captured.size();
        }
    }
}

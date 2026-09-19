package com.cleanerlogs.common;

import com.cleanerlogs.common.log4j.Log4jDetectionDiagnostics;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compatibility table of Minecraft packet-listener logger identities.
 *
 * <p>Every Minecraft packet listener declares {@code private static final Logger LOGGER = LogUtils.getLogger()},
 * which resolves to a Log4j2 logger named after the fully qualified runtime class name. That name is the
 * primary signal this project relies on, so every known identity lives here instead of being spread through the
 * code base.</p>
 *
 * <p>Only Mojang-mapped class names are listed. Mapped variants - Fabric intermediary names for example - are
 * resolved at runtime from the running platform by the platform adapter and are deliberately never hard coded,
 * because a single wrong entry would silently disable suppression without anybody noticing.</p>
 */
public final class MinecraftLoggerNames {

    /** Login listener: serves connections that have not reached PLAY. */
    public static final String LOGIN_LISTENER_CLASS = "net.minecraft.server.network.ServerLoginPacketListenerImpl";

    /** Configuration listener (1.20.2+): serves connections that have not reached PLAY. */
    public static final String CONFIGURATION_LISTENER_CLASS =
        "net.minecraft.server.network.ServerConfigurationPacketListenerImpl";

    /**
     * Game listener: serves real player sessions. A logger identity match here is a hard veto, the event is
     * never suppressed no matter what the message looks like.
     */
    public static final String PLAY_LISTENER_CLASS = "net.minecraft.server.network.ServerGamePacketListenerImpl";

    /** Trailing simple name of the login listener, used when only the package differs. */
    public static final String LOGIN_LISTENER_SIMPLE_NAME = "ServerLoginPacketListenerImpl";

    /** Trailing simple name of the configuration listener. */
    public static final String CONFIGURATION_LISTENER_SIMPLE_NAME = "ServerConfigurationPacketListenerImpl";

    /** Trailing simple name of the game listener. */
    public static final String PLAY_LISTENER_SIMPLE_NAME = "ServerGamePacketListenerImpl";

    /**
     * Legacy NMS login handler used by CraftBukkit and Spigot before 1.20.2. Registered as a simple name only
     * and kept in a dedicated constant so the intent is obvious.
     */
    public static final String LEGACY_LOGIN_LISTENER_SIMPLE_NAME = "LoginListener";

    /**
     * Shared superclass of the configuration and game listeners.
     *
     * <p>It is intentionally not classified. One logger identity serves both pre-PLAY and PLAY connections, so
     * treating it as pre-PLAY would hide genuine player disconnects. Events from this logger fall through to the
     * conservative message-structure check instead.</p>
     */
    public static final String SHARED_LISTENER_SIMPLE_NAME = "ServerCommonPacketListenerImpl";

    private static final Map<String, ConnectionPhase> NAMED_CLASSES = createNamedClasses();

    /**
     * @return an immutable map of Mojang-mapped listener class names to the phase they serve
     */
    public static Map<String, ConnectionPhase> namedLoggerClasses() {
        return NAMED_CLASSES;
    }

    /**
     * @return a builder pre-populated with every known logger identity and with this project's diagnostic logger
     *         excluded
     */
    public static LoggerIdentityMatcher.Builder defaultMatcherBuilder() {
        LoggerIdentityMatcher.Builder builder = LoggerIdentityMatcher.builder();
        for (Map.Entry<String, ConnectionPhase> entry : NAMED_CLASSES.entrySet()) {
            builder.addMinecraftClass(entry.getKey(), entry.getValue());
        }
        builder.addSimpleName(LEGACY_LOGIN_LISTENER_SIMPLE_NAME, ConnectionPhase.LOGIN);
        // Excluded so that diagnostic output can never be classified, and therefore never feed itself.
        builder.addIgnoredName(Log4jDetectionDiagnostics.LOGGER_NAME);
        return builder;
    }

    /**
     * @return the standard matcher without any platform-specific aliases
     */
    public static LoggerIdentityMatcher defaultMatcher() {
        return defaultMatcherBuilder().build();
    }

    private static Map<String, ConnectionPhase> createNamedClasses() {
        Map<String, ConnectionPhase> classes = new LinkedHashMap<>();
        classes.put(LOGIN_LISTENER_CLASS, ConnectionPhase.LOGIN);
        classes.put(CONFIGURATION_LISTENER_CLASS, ConnectionPhase.CONFIGURATION);
        classes.put(PLAY_LISTENER_CLASS, ConnectionPhase.PLAY);
        return Collections.unmodifiableMap(classes);
    }

    private MinecraftLoggerNames() {
    }
}

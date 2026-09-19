package com.cleanerlogs.fabric;

import com.cleanerlogs.common.RuntimeLog;
import com.cleanerlogs.common.SuppressionPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads the mod configuration from {@code config/prejoin-log-silencer.properties}.
 *
 * <p>A properties file is used on purpose: it needs no JSON or YAML library, which keeps the mod free of any
 * dependency beyond Fabric Loader. The file is created with documented defaults the first time the mod runs, so
 * the default behaviour always works with no configuration at all.</p>
 */
final class FabricSettings {

    private static final String FILE_NAME = "prejoin-log-silencer.properties";

    /** Name of the optional copy of the suppressed disconnect lines. */
    static final String SUPPRESSED_LOG_NAME = "prejoin-log-silencer-suppressed.log";

    private static final String DEFAULT_FILE = """
        # Pre-Join Log Silencer
        #
        # Removes the INFO "lost connection" line that Minecraft prints for connections that disconnect
        # during LOGIN or CONFIGURATION, before they reached the PLAY state and became real players.
        #
        # Genuine player disconnects, WARN and ERROR events, stack traces and crashes are untouched.
        # This does not identify or block bots.

        enabled=true
        suppress-login-disconnects=true
        suppress-configuration-disconnects=true
        # Write the totals to the log once an hour. A summary is written only when something was
        # actually suppressed, so a quiet server writes nothing. Fabric has no command, so this is on
        # by default here.
        log-suppressed-count=true
        # Copy every suppressed disconnect line to prejoin-log-silencer-suppressed.log.
        log-suppressed-to-file=false
        # Diagnostic mode for working out what a new Minecraft version changed.
        debug=false
        """;

    /**
     * The defaults Fabric ships with.
     *
     * <p>Fabric Loader has no command API and no server start event, and a command is not worth turning this
     * mod into one that needs Fabric API, mappings or mixins. The hourly summary is therefore on by default
     * here, so an operator still has a way to see whether the filter is doing anything.</p>
     */
    private static final SuppressionPolicy FABRIC_DEFAULTS =
        new SuppressionPolicy(true, true, true, true, false, false);

    private FabricSettings() {
    }

    /**
     * @param configDirectory the Fabric config directory
     * @param log             where problems are reported
     * @return the policy the configuration describes, or the Fabric defaults when the file cannot be read
     */
    static SuppressionPolicy load(Path configDirectory, RuntimeLog log) {
        Path file = configDirectory.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            writeDefaults(file, log);
            return FABRIC_DEFAULTS;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException error) {
            log.warn("Could not read " + file + "; falling back to the default configuration.", error);
            return FABRIC_DEFAULTS;
        }

        return new SuppressionPolicy(
            readBoolean(properties, "enabled", FABRIC_DEFAULTS.enabled()),
            readBoolean(properties, "suppress-login-disconnects", FABRIC_DEFAULTS.suppressLoginDisconnects()),
            readBoolean(properties, "suppress-configuration-disconnects",
                FABRIC_DEFAULTS.suppressConfigurationDisconnects()),
            readBoolean(properties, "log-suppressed-count", FABRIC_DEFAULTS.logSuppressedCount()),
            readBoolean(properties, "log-suppressed-to-file", FABRIC_DEFAULTS.logSuppressedToFile()),
            readBoolean(properties, "debug", FABRIC_DEFAULTS.debug()));
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static void writeDefaults(Path file, RuntimeLog log) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, DEFAULT_FILE, StandardCharsets.UTF_8);
            log.info("Wrote the default configuration to " + file + ".");
        } catch (IOException error) {
            log.warn("Could not write the default configuration to " + file
                + "; the built-in defaults are used instead.", error);
        }
    }
}

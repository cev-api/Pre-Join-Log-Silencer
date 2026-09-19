package com.cleanerlogs.bukkit;

import com.cleanerlogs.common.SuppressionPolicy;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Reads the plugin configuration into an immutable {@link SuppressionPolicy}.
 *
 * <p>Every key is optional. A server owner who never touches {@code config.yml} still gets the intended
 * behaviour, because the defaults below are the documented defaults.</p>
 */
final class BukkitSettings {

    private BukkitSettings() {
    }

    /**
     * @param configuration the Bukkit configuration, must not be {@code null}
     * @return the policy the configuration describes
     */
    static SuppressionPolicy read(FileConfiguration configuration) {
        return new SuppressionPolicy(
            configuration.getBoolean("enabled", true),
            configuration.getBoolean("suppress-login-disconnects", true),
            configuration.getBoolean("suppress-configuration-disconnects", true),
            configuration.getBoolean("log-suppressed-count", false),
            configuration.getBoolean("log-suppressed-to-file", false),
            configuration.getBoolean("debug", false));
    }
}

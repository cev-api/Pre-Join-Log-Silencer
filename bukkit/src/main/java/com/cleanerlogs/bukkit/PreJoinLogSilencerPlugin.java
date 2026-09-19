package com.cleanerlogs.bukkit;

import com.cleanerlogs.common.CleanerLogsRuntime;
import com.cleanerlogs.common.MinecraftLoggerNames;
import com.cleanerlogs.common.SuppressionPolicy;
import java.nio.file.Path;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit family bootstrap for CraftBukkit, Spigot, Paper, Purpur, Pufferfish and Folia.
 *
 * <p>The plugin installs a Log4j2 filter and exposes one operator command. It performs no world access, no
 * entity access, no scheduling and no thread-sensitive work, and it never touches a player, a connection or a
 * packet. That is what makes it genuinely Folia safe.</p>
 *
 * <p>It is compiled against the Bukkit API rather than any Paper-only API. The whole point is to work on plain
 * CraftBukkit and Spigot, and a logging filter needs no connection events at all.</p>
 */
public final class PreJoinLogSilencerPlugin extends JavaPlugin {

    /** The node an operator needs to use {@code /ls}. Kept in sync with {@code plugin.yml}. */
    public static final String COMMAND_PERMISSION = "prejoinlogsilencer.command";

    private static final String COMMAND_NAME = "ls";
    private static final String SUPPRESSED_LOG_NAME = "suppressed.log";

    private CleanerLogsRuntime runtime;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        runtime = new CleanerLogsRuntime(MinecraftLoggerNames.defaultMatcher(), readPolicy());
        runtime.setLog(new BukkitRuntimeLog(getLogger()));
        runtime.setSuppressedLogFile(suppressedLogPath());

        registerCommand();
        startIfEnabled();
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.stop();
            runtime = null;
        }
    }

    /**
     * Re-reads {@code config.yml} and applies it without a restart.
     */
    void reloadSilencer() {
        if (runtime == null) {
            return;
        }
        reloadConfig();
        runtime.setSuppressedLogFile(suppressedLogPath());
        runtime.applyPolicy(readPolicy());
        if (runtime.policy().enabled()) {
            runtime.start();
        } else {
            runtime.stop();
        }
        getLogger().info("Configuration reloaded: " + runtime.describeState());
    }

    /**
     * @return the runtime, or {@code null} before {@link #onEnable()} ran
     */
    CleanerLogsRuntime runtime() {
        return runtime;
    }

    private SuppressionPolicy readPolicy() {
        return BukkitSettings.read(getConfig());
    }

    private Path suppressedLogPath() {
        return getDataFolder().toPath().resolve(SUPPRESSED_LOG_NAME);
    }

    private void startIfEnabled() {
        if (!runtime.policy().enabled()) {
            return;
        }
        // start() reports its own outcome, including the single warning when the filter cannot be installed.
        runtime.start();
    }

    private void registerCommand() {
        PluginCommand command = getCommand(COMMAND_NAME);
        if (command == null) {
            getLogger().warning("Command " + COMMAND_NAME + " is missing from plugin.yml; statistics cannot be "
                + "inspected. Filtering is unaffected.");
            return;
        }
        SilencerCommand handler = new SilencerCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        // Nothing else is configured here on purpose. The permission in plugin.yml is what keeps the command out
        // of the command tree sent to a player who does not have it, and the executor returns silently for such
        // a sender, so a refusal message is never needed.
    }
}

package com.cleanerlogs.bukkit;

import com.cleanerlogs.common.CleanerLogsRuntime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * The {@code /ls} command.
 *
 * <p>Available to operators only. The command declares a permission in {@code plugin.yml}, which is what makes
 * servers omit it from the command tree they send to a player who does not have that permission. A sender
 * without the permission therefore gets no answer at all, rather than a refusal that would reveal that the
 * command exists. Tab completion returns nothing for everybody, so the sub-command names are never suggested
 * either.</p>
 *
 * <p>Answers are one line each, and the wording comes from {@link CleanerLogsRuntime#describeState()} so the
 * command and the console never disagree.</p>
 */
final class SilencerCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE = "Usage: /ls <debug|reload>";

    private final PreJoinLogSilencerPlugin plugin;

    SilencerCommand(PreJoinLogSilencerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Declared for documentation and for the command tree; the real gate is this check.
        if (!sender.hasPermission(PreJoinLogSilencerPlugin.COMMAND_PERMISSION)) {
            return true;
        }

        CleanerLogsRuntime runtime = plugin.runtime();
        if (runtime == null) {
            sender.sendMessage("not initialised");
            return true;
        }

        String subCommand = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "debug" -> sender.sendMessage(runtime.describeState());
            case "reload" -> {
                plugin.reloadSilencer();
                sender.sendMessage("reloaded | " + runtime.describeState());
            }
            default -> sender.sendMessage(USAGE);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Nothing is ever suggested, not even to operators.
        return Collections.emptyList();
    }
}

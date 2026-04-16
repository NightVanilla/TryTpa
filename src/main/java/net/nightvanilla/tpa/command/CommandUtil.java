package net.nightvanilla.tpa.command;

import lombok.experimental.UtilityClass;
import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.request.RequestStore;
import net.nightvanilla.tpa.request.RequestType;
import net.nightvanilla.tpa.util.MessageUtil;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@UtilityClass
public class CommandUtil {

    public static void register(String name, CommandExecutor exec, TabCompleter completer) {
        PluginCommand cmd = Objects.requireNonNull(TryTpa.getInstance().getCommand(name),
                "Command '" + name + "' is not declared in plugin.yml");
        cmd.setExecutor(exec);
        if (completer != null) cmd.setTabCompleter(completer);
    }

    public static boolean checkPermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(MessageUtil.get("Messages.NoPermission"));
            return false;
        }
        return true;
    }

    /** Returns the last argument, lower-cased, for prefix matching in tab completion. */
    public static String lastArg(String[] args) {
        return args[args.length - 1].toLowerCase();
    }

    /** Filters {@code options} to those that start with the last arg, sorted. */
    public static List<String> filterPrefix(List<String> options, String[] args) {
        String prefix = lastArg(args);
        return options.stream().filter(c -> c.toLowerCase().startsWith(prefix)).sorted().toList();
    }

    /** Names of players with an outstanding request of {@code type} targeting {@code player}. */
    public static List<String> requesterNames(RequestType type, Player player) {
        RequestStore store = TryTpa.getInstance().getRequestStore();
        List<String> names = new ArrayList<>();
        for (UUID uuid : store.getRequestersForTarget(type, player.getUniqueId())) {
            String name = store.resolvePlayerName(uuid);
            if (name != null) names.add(name);
        }
        return names;
    }

    public static void syntax(CommandSender sender, String command) {
        sender.sendMessage(MessageUtil.get("Messages.CommandSyntax").replace("%command%", command));
    }
}

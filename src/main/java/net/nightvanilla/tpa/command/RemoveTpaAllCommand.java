package net.nightvanilla.tpa.command;

import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class RemoveTpaAllCommand implements CommandExecutor, TabCompleter {

    public RemoveTpaAllCommand() {
        CommandUtil.register("removetpaall", this, this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!CommandUtil.checkPermission(sender, "trytpa.command.removetpaall")) return false;

        if (args.length == 2) {
            String player = args[0];
            int days;
            try {
                days = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                TryTpa.getInstance().getLogger().warning("Invalid days argument for /removetpaall: '" + args[1] + "'");
                CommandUtil.syntax(sender, "removetpaall <player> <days>");
                return false;
            }

            if (days > 0) {
                if (Bukkit.getPlayerUniqueId(player) == null) {
                    sender.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
                    return false;
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "lp user " + player + " permission settemp trytpa.command.tpaall false " + days + "d");
                sender.sendMessage(MessageUtil.get("Messages.Removed").replace("%player%", player));
                return false;
            }
        }

        CommandUtil.syntax(sender, "removetpaall <player> <days>");
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return CommandUtil.filterPrefix(
                    Arrays.stream(Bukkit.getOfflinePlayers()).map(OfflinePlayer::getName).filter(n -> n != null).toList(),
                    args);
        }
        if (args.length == 2) {
            return CommandUtil.filterPrefix(List.of("1", "2", "3", "5", "7", "14", "30"), args);
        }
        return List.of();
    }
}

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

import java.util.*;

public class RemoveTpaAllCommand implements CommandExecutor, TabCompleter {

    public RemoveTpaAllCommand() {
        Objects.requireNonNull(TryTpa.getInstance().getCommand("removetpaall")).setExecutor(this);
        Objects.requireNonNull(TryTpa.getInstance().getCommand("removetpaall")).setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!(sender.hasPermission("trytpa.command.removetpaall"))) {
            sender.sendMessage(MessageUtil.get("Messages.NoPermission"));
            return false;
        }

        if (args.length == 2) {
            try {
                String player = args[0];
                int days = Integer.parseInt(args[1]);

                if (days > 0) {
                    if (Bukkit.getPlayerUniqueId(player) == null) {
                        sender.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
                        return false;
                    }

                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player + " permission settemp trytpa.command.tpaall false " + days + "d");
                    sender.sendMessage(MessageUtil.get("Messages.Removed").replaceAll("%player%", player));
                    return false;
                }
            } catch (NumberFormatException ignored) { }
        }

        sender.sendMessage(MessageUtil.get("Messages.CommandSyntax").replaceAll("%command%", "removetpaall <player> <days>"));
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        List<String> list = new ArrayList<>();

        if (args.length == 2) {
            list.addAll(Arrays.asList("1", "2", "3", "5", "7", "14", "30"));
        }

        if (args.length == 1) {
            list.addAll(Arrays.stream(Bukkit.getOfflinePlayers()).map(OfflinePlayer::getName).toList());
        }

        return list.stream().filter(content -> content.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).sorted().toList();
    }

}

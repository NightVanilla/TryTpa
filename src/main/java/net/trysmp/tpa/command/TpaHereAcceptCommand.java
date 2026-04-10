package net.trysmp.tpa.command;

import net.trysmp.tpa.TryTpa;
import net.trysmp.tpa.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TpaHereAcceptCommand implements CommandExecutor, TabCompleter {

    public TpaHereAcceptCommand() {
        Objects.requireNonNull(TryTpa.getInstance().getCommand("tpahereaccept")).setExecutor(this);
        Objects.requireNonNull(TryTpa.getInstance().getCommand("tpahereaccept")).setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (!player.hasPermission("trytpa.command.tpahere")) {
            player.sendMessage(MessageUtil.get("Messages.NoPermission"));
            return false;
        }

        if (args.length == 0) {
            TpaHereCommand.accept(player);
            return false;
        }

        if (args.length == 1) {
            TpaHereCommand.accept(player, args[0]);
            return false;
        }

        player.sendMessage(MessageUtil.get("Messages.CommandSyntax").replaceAll("%command%", "tpahereaccept <player>"));
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        List<String> list = new ArrayList<>();

        if (args.length == 1 && sender instanceof Player player) {
            for (UUID uuid : TryTpa.getInstance().getRequestStore().getTpaHereRequestersForTarget(player.getUniqueId())) {
                Player requester = Bukkit.getPlayer(uuid);
                if (requester != null) list.add(requester.getName());
            }
        }

        return list.stream().filter(c -> c.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).sorted().toList();
    }

}

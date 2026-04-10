package net.trysmp.tpa.command;

import net.kyori.adventure.text.Component;
import net.trysmp.tpa.RequestStore;
import net.trysmp.tpa.TryTpa;
import net.trysmp.tpa.util.DateUtil;
import net.trysmp.tpa.util.MessageUtil;
import net.trysmp.tpa.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TpaAllCommand implements CommandExecutor, TabCompleter {

    public TpaAllCommand() {
        Objects.requireNonNull(TryTpa.getInstance().getCommand("tpaall")).setExecutor(this);
        Objects.requireNonNull(TryTpa.getInstance().getCommand("tpaall")).setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
                return false;
            }

            Location location = TryTpa.getInstance().getRequestStore().getTpaAllRequest(target.getUniqueId());
            if (location != null) {
                TeleportUtil.teleport(player, location);
                player.sendMessage(MessageUtil.get("Messages.Accepted"));
            } else {
                player.sendMessage(MessageUtil.get("Messages.Expired"));
            }
            return false;
        }

        if (!player.hasPermission("trytpa.command.tpaall")) {
            player.sendMessage(MessageUtil.get("Messages.NoPermission"));
            return false;
        }

        RequestStore store = TryTpa.getInstance().getRequestStore();
        long delay = store.getTpaAllCooldown(player.getUniqueId());
        if (delay > System.currentTimeMillis()) {
            player.sendMessage(MessageUtil.get("Messages.CommandDelay").replaceAll("%time%", DateUtil.secondsToTime((delay - System.currentTimeMillis()) / 1000)));
            return false;
        }

        if (args.length == 0) {
            Component message = MessageUtil.getRequest("TpaAll", player.getName());

            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!target.equals(player)) {
                    target.sendMessage(message);
                    if (TryTpa.getInstance().getConfig().getBoolean("Settings.Sounds.TpaAll")) {
                        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 5, 5);
                    }
                }
            }

            player.sendMessage(MessageUtil.get("Messages.Sent"));

            long expiration = TryTpa.getInstance().getConfig().getLong("Settings.Expiration.TpaAll");
            store.putTpaAllRequest(player.getUniqueId(), player.getLocation(), expiration);

            if (!player.hasPermission("trytpa.bypass.cooldown")) {
                store.setTpaAllCooldown(player.getUniqueId(), System.currentTimeMillis() + TryTpa.getInstance().getConfig().getLong("Settings.Cooldown.TpaAll"));
            }

            return false;
        }

        player.sendMessage(MessageUtil.get("Messages.CommandSyntax").replaceAll("%command%", "tpaall"));
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        return new ArrayList<>();
    }

}

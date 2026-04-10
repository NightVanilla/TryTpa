package net.nightvanilla.tpa.command;

import net.nightvanilla.tpa.RequestStore;
import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.util.DateUtil;
import net.nightvanilla.tpa.util.MessageUtil;
import net.nightvanilla.tpa.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TpaCommand implements CommandExecutor, TabCompleter {

    public TpaCommand() {
        Objects.requireNonNull(TryTpa.getInstance().getCommand("tpa")).setExecutor(this);
        Objects.requireNonNull(TryTpa.getInstance().getCommand("tpa")).setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (!player.hasPermission("trytpa.command.tpa")) {
            player.sendMessage(MessageUtil.get("Messages.NoPermission"));
            return false;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            if (args[1].equalsIgnoreCase("*")) {
                acceptAll(player);
            } else {
                accept(player, args[1]);
            }
            return false;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("accept")) {
            accept(player);
            return false;
        }

        RequestStore store = TryTpa.getInstance().getRequestStore();
        long delay = store.getTpaCooldown(player.getUniqueId());
        if (delay > System.currentTimeMillis()) {
            player.sendMessage(MessageUtil.get("Messages.CommandDelay").replaceAll("%time%", DateUtil.secondsToTime((delay - System.currentTimeMillis()) / 1000)));
            return false;
        }

        if (args.length == 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
                return false;
            }

            if (player.equals(target)) {
                player.sendMessage(MessageUtil.get("Messages.NotYourself"));
                return false;
            }

            player.sendMessage(MessageUtil.get("Messages.Sent"));
            target.sendMessage(MessageUtil.getRequest("Tpa", player.getName()));

            if (TryTpa.getInstance().getConfig().getBoolean("Settings.Sounds.Tpa")) {
                target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 5, 5);
            }

            long expiration = TryTpa.getInstance().getConfig().getLong("Settings.Expiration.Tpa");
            store.putTpaRequest(player.getUniqueId(), target.getUniqueId(), expiration);

            if (!player.hasPermission("trytpa.bypass.cooldown")) {
                store.setTpaCooldown(player.getUniqueId(), System.currentTimeMillis() + TryTpa.getInstance().getConfig().getLong("Settings.Cooldown.Tpa"));
            }

            return false;
        }

        player.sendMessage(MessageUtil.get("Messages.CommandSyntax").replaceAll("%command%", "tpa <player>"));
        player.sendMessage(MessageUtil.get("Messages.CommandSyntax").replaceAll("%command%", "tpa accept <player / *>"));
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        List<String> list = new ArrayList<>();

        if (args.length == 2 && sender instanceof Player player && args[0].equalsIgnoreCase("accept")) {
            for (UUID uuid : TryTpa.getInstance().getRequestStore().getTpaRequestersForTarget(player.getUniqueId())) {
                Player requester = Bukkit.getPlayer(uuid);
                if (requester != null) list.add(requester.getName());
            }
            list.add("*");
        }

        if (args.length == 1) {
            list.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            list.add("accept");
        }

        return list.stream().filter(c -> c.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).sorted().toList();
    }

    public static void accept(Player player) {
        for (UUID requesterUUID : TryTpa.getInstance().getRequestStore().getTpaRequestersForTarget(player.getUniqueId())) {
            Player requester = Bukkit.getPlayer(requesterUUID);
            if (requester != null) {
                accept(player, requester.getName());
                return;
            }
        }
        player.sendMessage(MessageUtil.get("Messages.NoRequests"));
    }

    public static void acceptAll(Player player) {
        RequestStore store = TryTpa.getInstance().getRequestStore();
        Set<UUID> requesters = store.getTpaRequestersForTarget(player.getUniqueId());

        List<Player> toTeleport = new ArrayList<>();
        for (UUID requesterUUID : requesters) {
            Player requester = Bukkit.getPlayer(requesterUUID);
            if (requester != null) toTeleport.add(requester);
        }

        if (toTeleport.isEmpty()) {
            player.sendMessage(MessageUtil.get("Messages.NoRequests"));
            return;
        }

        for (Player requester : toTeleport) {
            requester.sendMessage(MessageUtil.get("Messages.AcceptedOther").replaceAll("%player%", player.getName()));
            store.removeTpaRequest(requester.getUniqueId());
            TeleportUtil.teleport(requester, player.getLocation());
        }

        player.sendMessage(MessageUtil.get("Messages.AcceptedAll"));
    }

    public static void accept(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
            return;
        }

        if (player.equals(target)) {
            player.sendMessage(MessageUtil.get("Messages.NotYourself"));
            return;
        }

        RequestStore store = TryTpa.getInstance().getRequestStore();
        UUID requestTarget = store.getTpaRequest(target.getUniqueId());

        if (requestTarget != null && requestTarget.equals(player.getUniqueId())) {
            store.removeTpaRequest(target.getUniqueId());
            target.sendMessage(MessageUtil.get("Messages.AcceptedOther").replaceAll("%player%", player.getName()));
            TeleportUtil.teleport(target, player.getLocation());
            player.sendMessage(MessageUtil.get("Messages.Accepted"));
        } else {
            player.sendMessage(MessageUtil.get("Messages.Expired"));
        }
    }

}

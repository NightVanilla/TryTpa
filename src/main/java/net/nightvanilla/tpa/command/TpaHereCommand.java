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

public class TpaHereCommand implements CommandExecutor, TabCompleter {

    public TpaHereCommand() {
        var cmd = Objects.requireNonNull(TryTpa.getInstance().getCommand("tpahere"));
        cmd.setExecutor(this);
        cmd.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (!player.hasPermission("trytpa.command.tpahere")) {
            player.sendMessage(MessageUtil.get("Messages.NoPermission"));
            return false;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            accept(player, args[1]);
            return false;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("accept")) {
            accept(player);
            return false;
        }

        RequestStore store = TryTpa.getInstance().getRequestStore();
        long delay = store.getTpaHereCooldown(player.getUniqueId());
        if (delay > System.currentTimeMillis()) {
            player.sendMessage(MessageUtil.get("Messages.CommandDelay").replace("%time%", DateUtil.secondsToTime((delay - System.currentTimeMillis()) / 1000)));
            return false;
        }

        if (args.length == 1) {
            Player target = Bukkit.getPlayer(args[0]);

            if (target == null) {
                // Try to reach a player on another server via Redis
                UUID targetUUID = store.resolvePlayerUUID(args[0]);
                if (targetUUID != null && TryTpa.getInstance().getRedisManager().isAvailable()) {
                    long expiration = TryTpa.getInstance().getConfig().getLong("Settings.Expiration.TpaHere");
                    store.putTpaHereRequest(player.getUniqueId(), targetUUID, expiration);
                    // Store this server's name so the target knows where to come back
                    String thisServer = TryTpa.getInstance().getConfig().getString("Server.Name", "");
                    TryTpa.getInstance().getRedisManager().setRequestServerName("tpahere", player.getUniqueId(), thisServer, expiration);
                    TryTpa.getInstance().getRedisManager().publishToPlayer(targetUUID, "TPAHERE:" + player.getName());
                    player.sendMessage(MessageUtil.get("Messages.Sent"));
                    if (!player.hasPermission("trytpa.bypass.cooldown")) {
                        store.setTpaHereCooldown(player.getUniqueId(), System.currentTimeMillis() + TryTpa.getInstance().getConfig().getLong("Settings.Cooldown.TpaHere"));
                    }
                    return false;
                }
                player.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
                return false;
            }

            if (player.equals(target)) {
                player.sendMessage(MessageUtil.get("Messages.NotYourself"));
                return false;
            }

            player.sendMessage(MessageUtil.get("Messages.Sent"));
            target.sendMessage(MessageUtil.getRequest("TpaHere", player.getName()));

            if (TryTpa.getInstance().getConfig().getBoolean("Settings.Sounds.TpaHere")) {
                target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 5, 5);
            }

            long expiration = TryTpa.getInstance().getConfig().getLong("Settings.Expiration.TpaHere");
            store.putTpaHereRequest(player.getUniqueId(), target.getUniqueId(), expiration);

            if (!player.hasPermission("trytpa.bypass.cooldown")) {
                store.setTpaHereCooldown(player.getUniqueId(), System.currentTimeMillis() + TryTpa.getInstance().getConfig().getLong("Settings.Cooldown.TpaHere"));
            }

            return false;
        }

        player.sendMessage(MessageUtil.get("Messages.CommandSyntax").replace("%command%", "tpahere <player>"));
        player.sendMessage(MessageUtil.get("Messages.CommandSyntax").replace("%command%", "tpahere accept <player>"));
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        List<String> list = new ArrayList<>();

        if (args.length == 2 && sender instanceof Player player && args[0].equalsIgnoreCase("accept")) {
            RequestStore store = TryTpa.getInstance().getRequestStore();
            for (UUID uuid : store.getTpaHereRequestersForTarget(player.getUniqueId())) {
                String name = store.resolvePlayerName(uuid);
                if (name != null) list.add(name);
            }
        }

        if (args.length == 1) {
            list.addAll(TryTpa.getInstance().getRequestStore().getOnlinePlayerNames());
            list.add("accept");
        }

        return list.stream().filter(c -> c.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).sorted().toList();
    }

    public static void accept(Player player) {
        RequestStore store = TryTpa.getInstance().getRequestStore();
        String crossServerRequester = null;
        for (UUID requesterUUID : store.getTpaHereRequestersForTarget(player.getUniqueId())) {
            Player requester = Bukkit.getPlayer(requesterUUID);
            if (requester != null) {
                accept(player, requester.getName());
                return;
            }
            String name = store.resolvePlayerName(requesterUUID);
            if (name != null) {
                crossServerRequester = name;
                break;
            }
        }
        if (crossServerRequester != null) {
            accept(player, crossServerRequester);
        } else {
            player.sendMessage(MessageUtil.get("Messages.NoRequests"));
        }
    }

    public static void accept(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            // Requester is on another server — handle cross-server accept
            UUID requesterUUID = TryTpa.getInstance().getRequestStore().resolvePlayerUUID(targetName);
            if (requesterUUID != null && TryTpa.getInstance().getRedisManager().isAvailable()) {
                RequestStore store = TryTpa.getInstance().getRequestStore();
                UUID requestTarget = store.getTpaHereRequest(requesterUUID);
                if (requestTarget == null || !requestTarget.equals(player.getUniqueId())) {
                    player.sendMessage(MessageUtil.get("Messages.Expired"));
                    return;
                }
                store.removeTpaHereRequest(requesterUUID);
                // Retrieve requester's server name (stored when the request was made)
                String requesterServer = TryTpa.getInstance().getRedisManager().getRequestServerName("tpahere", requesterUUID);
                TryTpa.getInstance().getRedisManager().removeRequestServerName("tpahere", requesterUUID);
                if (requesterServer == null || requesterServer.isEmpty()) {
                    player.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
                    return;
                }
                // Store pending teleport: player will TP to requester on arrival
                long ttl = TryTpa.getInstance().getConfig().getLong("Settings.Expiration.TpaHere");
                TryTpa.getInstance().getRedisManager().setPendingTpaHere(player.getUniqueId(), requesterUUID, ttl);
                // Notify requester that their request was accepted
                TryTpa.getInstance().getRedisManager().publishToPlayer(requesterUUID, "TPAHERE_ACCEPTED:" + player.getName());
                // Connect player to requester's server
                TryTpa.getInstance().connectPlayerToServer(player, requesterServer);
                player.sendMessage(MessageUtil.get("Messages.Accepted"));
                return;
            }
            player.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
            return;
        }

        if (player.equals(target)) {
            player.sendMessage(MessageUtil.get("Messages.NotYourself"));
            return;
        }

        RequestStore store = TryTpa.getInstance().getRequestStore();
        UUID requestTarget = store.getTpaHereRequest(target.getUniqueId());

        if (requestTarget != null && requestTarget.equals(player.getUniqueId())) {
            store.removeTpaHereRequest(target.getUniqueId());
            target.sendMessage(MessageUtil.get("Messages.AcceptedOther").replace("%player%", player.getName()));
            player.sendMessage(MessageUtil.get("Messages.Accepted"));
            TeleportUtil.teleport(player, target.getLocation());
        } else {
            player.sendMessage(MessageUtil.get("Messages.Expired"));
        }
    }

}

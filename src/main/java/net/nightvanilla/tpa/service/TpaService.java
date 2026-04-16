package net.nightvanilla.tpa.service;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;
import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.config.Settings;
import net.nightvanilla.tpa.redis.RedisManager;
import net.nightvanilla.tpa.request.RequestStore;
import net.nightvanilla.tpa.request.RequestType;
import net.nightvanilla.tpa.util.DateUtil;
import net.nightvanilla.tpa.util.MessageUtil;
import net.nightvanilla.tpa.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * All TPA business logic. Commands stay thin and delegate here.
 *
 * <p>Cross-server flows use Redis pub/sub plus BungeeCord plugin messages:
 * <ul>
 *   <li>Requester sends on server A; target gets notification on server B.</li>
 *   <li>On accept, the side that needs to travel is connected to the other server.</li>
 *   <li>A pending entry in Redis tells the arriving server to teleport on join.</li>
 * </ul>
 */
public final class TpaService {

    private final TryTpa plugin;
    private final Settings settings;
    private final RequestStore store;
    private final RedisManager redis;

    public TpaService(TryTpa plugin, Settings settings, RequestStore store, RedisManager redis) {
        this.plugin = plugin;
        this.settings = settings;
        this.store = store;
        this.redis = redis;
    }

    // ---------------------------------------------------------------------
    // /tpa and /tpahere — send request
    // ---------------------------------------------------------------------

    /** Sends a TPA or TPAHERE request from {@code sender} to {@code targetName}. */
    public void sendRequest(RequestType type, Player sender, String targetName) {
        if (type == RequestType.TPA_ALL) throw new IllegalArgumentException("Use broadcastTpaAll");

        if (onCooldown(type, sender)) return;

        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null) {
            sendLocalRequest(type, sender, target);
            return;
        }

        UUID targetUUID = store.resolvePlayerUUID(targetName);
        if (targetUUID != null && redis.isAvailable()) {
            sendCrossServerRequest(type, sender, targetUUID, targetName);
            return;
        }

        sender.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
    }

    private void sendLocalRequest(RequestType type, Player sender, Player target) {
        if (sender.equals(target)) {
            sender.sendMessage(MessageUtil.get("Messages.NotYourself"));
            return;
        }
        if (store.isDisabled(type, target.getUniqueId())) {
            sender.sendMessage(disabledMessage(type).replace("%player%", target.getName()));
            return;
        }

        target.sendMessage(MessageUtil.getRequest(type.getConfigKey(), sender.getName()));
        playRequestSound(type, target);

        store.putRequest(type, sender.getUniqueId(), target.getUniqueId(),
                expirationSeconds(type), settings.getServerName());

        sender.sendMessage(MessageUtil.get("Messages.Sent"));
        applyCooldown(type, sender);
    }

    private void sendCrossServerRequest(RequestType type, Player sender, UUID targetUUID, String targetName) {
        if (store.isDisabled(type, targetUUID)) {
            sender.sendMessage(disabledMessage(type).replace("%player%", targetName));
            return;
        }

        long ttl = expirationSeconds(type);
        store.putRequest(type, sender.getUniqueId(), targetUUID, ttl, settings.getServerName());
        redis.publishToPlayer(targetUUID, type.getConfigKey().toUpperCase() + ":" + sender.getName());

        sender.sendMessage(MessageUtil.get("Messages.Sent"));
        applyCooldown(type, sender);
    }

    // ---------------------------------------------------------------------
    // /tpaall — broadcast + accept
    // ---------------------------------------------------------------------

    public void broadcastTpaAll(Player sender) {
        if (onCooldown(RequestType.TPA_ALL, sender)) return;

        Component message = MessageUtil.getRequest(RequestType.TPA_ALL.getConfigKey(), sender.getName());

        // Local notifications (sound + message)
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(sender)) continue;
            if (store.isDisabled(RequestType.TPA_ALL, online.getUniqueId())) continue;
            online.sendMessage(message);
            playRequestSound(RequestType.TPA_ALL, online);
        }

        long ttl = expirationSeconds(RequestType.TPA_ALL);
        store.putTpaAllRequest(sender.getUniqueId(), sender.getLocation(), settings.getServerName(), ttl);

        // Notify all cross-server players who opted in
        if (redis.isAvailable()) {
            for (String name : redis.getCachedPlayerNames()) {
                Player local = Bukkit.getPlayerExact(name);
                if (local != null) continue; // already notified above
                UUID uuid = store.resolvePlayerUUID(name);
                if (uuid == null) continue;
                if (store.isDisabled(RequestType.TPA_ALL, uuid)) continue;
                redis.publishToPlayer(uuid, "TPAALL:" + sender.getName());
            }
        }

        sender.sendMessage(MessageUtil.get("Messages.Sent"));
        applyCooldown(RequestType.TPA_ALL, sender);
    }

    public void acceptTpaAll(Player accepter, String requesterName) {
        UUID requesterUUID = store.resolvePlayerUUID(requesterName);
        if (requesterUUID == null) {
            accepter.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
            return;
        }

        String serverName = store.getTpaAllServerName(requesterUUID);
        Location location = store.getTpaAllLocation(requesterUUID);

        // Cross-server: the requester's location world isn't on this server.
        if (redis.isAvailable() && serverName != null && !serverName.isEmpty()
                && !serverName.equalsIgnoreCase(settings.getServerName())) {
            String payload = redis.getTpaAllRawPayload(requesterUUID);
            if (payload == null) {
                accepter.sendMessage(MessageUtil.get("Messages.Expired"));
                return;
            }
            redis.setPending(RedisManager.PendingKind.TPA_ALL, accepter.getUniqueId(), payload, expirationSeconds(RequestType.TPA_ALL));
            accepter.sendMessage(MessageUtil.get("Messages.Accepted"));
            connectPlayerToServer(accepter, serverName);
            return;
        }

        if (location == null) {
            accepter.sendMessage(MessageUtil.get("Messages.Expired"));
            return;
        }

        TeleportUtil.teleport(accepter, location);
        accepter.sendMessage(MessageUtil.get("Messages.Accepted"));
    }

    // ---------------------------------------------------------------------
    // Accept flows
    // ---------------------------------------------------------------------

    /**
     * Accepts the single outstanding request of {@code type} aimed at {@code accepter}.
     * Prefers a locally-online requester; falls back to any cross-server requester.
     */
    public void acceptAny(RequestType type, Player accepter) {
        Set<UUID> requesters = store.getRequestersForTarget(type, accepter.getUniqueId());

        String crossServerName = null;
        for (UUID requesterUUID : requesters) {
            Player requester = Bukkit.getPlayer(requesterUUID);
            if (requester != null) {
                accept(type, accepter, requester.getName());
                return;
            }
            if (crossServerName == null) {
                String name = store.resolvePlayerName(requesterUUID);
                if (name != null) crossServerName = name;
            }
        }

        if (crossServerName != null) {
            accept(type, accepter, crossServerName);
        } else {
            accepter.sendMessage(MessageUtil.get("Messages.NoRequests"));
        }
    }

    public void acceptAll(RequestType type, Player accepter) {
        if (type == RequestType.TPA_ALL) throw new IllegalArgumentException("Not applicable to TPA_ALL");

        Set<UUID> requesters = store.getRequestersForTarget(type, accepter.getUniqueId());
        if (requesters.isEmpty()) {
            accepter.sendMessage(MessageUtil.get("Messages.NoRequests"));
            return;
        }

        List<Player> localRequesters = new ArrayList<>();
        List<UUID> crossRequesters = new ArrayList<>();
        for (UUID uuid : requesters) {
            Player local = Bukkit.getPlayer(uuid);
            if (local != null) localRequesters.add(local);
            else crossRequesters.add(uuid);
        }

        Location targetLoc = accepter.getLocation();

        for (Player requester : localRequesters) {
            if (type == RequestType.TPA) {
                requester.sendMessage(MessageUtil.get("Messages.AcceptedOther").replace("%player%", accepter.getName()));
                store.removeRequest(type, requester.getUniqueId());
                TeleportUtil.teleport(requester, targetLoc);
            } else { // TPA_HERE
                accept(type, accepter, requester.getName()); // run normal tpahere flow
            }
        }

        for (UUID requesterUUID : crossRequesters) {
            String name = store.resolvePlayerName(requesterUUID);
            if (name != null) accept(type, accepter, name);
        }

        accepter.sendMessage(MessageUtil.get("Messages.AcceptedAll"));
    }

    /** Accept a specific named requester. */
    public void accept(RequestType type, Player accepter, String requesterName) {
        if (type == RequestType.TPA) acceptTpa(accepter, requesterName);
        else if (type == RequestType.TPA_HERE) acceptTpaHere(accepter, requesterName);
        else throw new IllegalArgumentException("Use acceptTpaAll for /tpaall accept");
    }

    private void acceptTpa(Player accepter, String requesterName) {
        Player requester = Bukkit.getPlayerExact(requesterName);
        if (requester != null) {
            if (accepter.equals(requester)) {
                accepter.sendMessage(MessageUtil.get("Messages.NotYourself"));
                return;
            }
            UUID wanted = store.getRequest(RequestType.TPA, requester.getUniqueId());
            if (wanted == null || !wanted.equals(accepter.getUniqueId())) {
                accepter.sendMessage(MessageUtil.get("Messages.Expired"));
                return;
            }
            store.removeRequest(RequestType.TPA, requester.getUniqueId());
            requester.sendMessage(MessageUtil.get("Messages.AcceptedOther").replace("%player%", accepter.getName()));
            TeleportUtil.teleport(requester, accepter.getLocation());
            accepter.sendMessage(MessageUtil.get("Messages.Accepted"));
            return;
        }

        // Requester is on another server — bring them here.
        UUID requesterUUID = store.resolvePlayerUUID(requesterName);
        if (requesterUUID == null || !redis.isAvailable()) {
            accepter.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
            return;
        }
        UUID wanted = store.getRequest(RequestType.TPA, requesterUUID);
        if (wanted == null || !wanted.equals(accepter.getUniqueId())) {
            accepter.sendMessage(MessageUtil.get("Messages.Expired"));
            return;
        }
        store.removeRequest(RequestType.TPA, requesterUUID);

        long ttl = expirationSeconds(RequestType.TPA);
        redis.setPending(RedisManager.PendingKind.TPA, requesterUUID, accepter.getUniqueId().toString(), ttl);
        redis.publishToPlayer(requesterUUID, "TPA_CONNECT:" + settings.getServerName() + ":" + accepter.getName());
        accepter.sendMessage(MessageUtil.get("Messages.Accepted"));
    }

    private void acceptTpaHere(Player accepter, String requesterName) {
        Player requester = Bukkit.getPlayerExact(requesterName);
        if (requester != null) {
            if (accepter.equals(requester)) {
                accepter.sendMessage(MessageUtil.get("Messages.NotYourself"));
                return;
            }
            UUID wanted = store.getRequest(RequestType.TPA_HERE, requester.getUniqueId());
            if (wanted == null || !wanted.equals(accepter.getUniqueId())) {
                accepter.sendMessage(MessageUtil.get("Messages.Expired"));
                return;
            }
            store.removeRequest(RequestType.TPA_HERE, requester.getUniqueId());
            requester.sendMessage(MessageUtil.get("Messages.AcceptedOther").replace("%player%", accepter.getName()));
            accepter.sendMessage(MessageUtil.get("Messages.Accepted"));
            TeleportUtil.teleport(accepter, requester.getLocation());
            return;
        }

        // Requester is on another server — send accepter there.
        UUID requesterUUID = store.resolvePlayerUUID(requesterName);
        if (requesterUUID == null || !redis.isAvailable()) {
            accepter.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
            return;
        }
        UUID wanted = store.getRequest(RequestType.TPA_HERE, requesterUUID);
        if (wanted == null || !wanted.equals(accepter.getUniqueId())) {
            accepter.sendMessage(MessageUtil.get("Messages.Expired"));
            return;
        }
        String requesterServer = store.getRequestServerName(RequestType.TPA_HERE, requesterUUID);
        if (requesterServer == null || requesterServer.isEmpty()) {
            accepter.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
            return;
        }
        store.removeRequest(RequestType.TPA_HERE, requesterUUID);

        long ttl = expirationSeconds(RequestType.TPA_HERE);
        redis.setPending(RedisManager.PendingKind.TPA_HERE, accepter.getUniqueId(), requesterUUID.toString(), ttl);
        redis.publishToPlayer(requesterUUID, "TPAHERE_ACCEPTED:" + accepter.getName());
        accepter.sendMessage(MessageUtil.get("Messages.Accepted"));
        connectPlayerToServer(accepter, requesterServer);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    public void connectPlayerToServer(Player player, String serverName) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to connect " + player.getName() + " to " + serverName + ": " + e.getMessage());
        }
    }

    private boolean onCooldown(RequestType type, Player sender) {
        if (sender.hasPermission("trytpa.bypass.cooldown")) return false;
        long expiry = store.getCooldown(type, sender.getUniqueId());
        if (expiry > System.currentTimeMillis()) {
            sender.sendMessage(MessageUtil.get("Messages.CommandDelay")
                    .replace("%time%", DateUtil.secondsToTime((expiry - System.currentTimeMillis()) / 1000)));
            return true;
        }
        return false;
    }

    private void applyCooldown(RequestType type, Player sender) {
        if (sender.hasPermission("trytpa.bypass.cooldown")) return;
        store.setCooldown(type, sender.getUniqueId(), System.currentTimeMillis() + cooldownMillis(type));
    }

    private long expirationSeconds(RequestType type) {
        return switch (type) {
            case TPA -> settings.getTpaExpirationSeconds();
            case TPA_HERE -> settings.getTpaHereExpirationSeconds();
            case TPA_ALL -> settings.getTpaAllExpirationSeconds();
        };
    }

    private long cooldownMillis(RequestType type) {
        return switch (type) {
            case TPA -> settings.getTpaCooldownMillis();
            case TPA_HERE -> settings.getTpaHereCooldownMillis();
            case TPA_ALL -> settings.getTpaAllCooldownMillis();
        };
    }

    private boolean soundEnabled(RequestType type) {
        return switch (type) {
            case TPA -> settings.isTpaSound();
            case TPA_HERE -> settings.isTpaHereSound();
            case TPA_ALL -> settings.isTpaAllSound();
        };
    }

    private void playRequestSound(RequestType type, Player target) {
        if (soundEnabled(type)) {
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 5, 5);
        }
    }

    private static String disabledMessage(RequestType type) {
        return MessageUtil.get("Messages." + type.getConfigKey() + "DisabledByTarget");
    }
}

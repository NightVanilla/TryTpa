package net.nightvanilla.tpa.listener;

import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.redis.RedisManager;
import net.nightvanilla.tpa.util.MessageUtil;
import net.nightvanilla.tpa.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public final class PlayerJoinListener implements Listener {

    /** Short delay so the player is fully loaded before we teleport. */
    private static final long JOIN_TELEPORT_DELAY_TICKS = 5L;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        RedisManager redis = TryTpa.getInstance().getRedisManager();
        redis.registerPlayer(player.getUniqueId(), player.getName());

        if (!redis.isAvailable()) return;

        // TPA: teleport this player TO the stored target UUID
        String tpaTarget = redis.getPending(RedisManager.PendingKind.TPA, player.getUniqueId());
        if (tpaTarget != null) {
            redis.removePending(RedisManager.PendingKind.TPA, player.getUniqueId());
            scheduleTeleportToPlayer(player, parseUUID(tpaTarget));
            return;
        }

        // TPAHERE: teleport this player TO the stored requester UUID (on this server)
        String tpaHereRequester = redis.getPending(RedisManager.PendingKind.TPA_HERE, player.getUniqueId());
        if (tpaHereRequester != null) {
            redis.removePending(RedisManager.PendingKind.TPA_HERE, player.getUniqueId());
            scheduleTeleportToPlayer(player, parseUUID(tpaHereRequester));
            return;
        }

        // TPAALL: teleport this player to a decoded location
        String tpaAllPayload = redis.getPending(RedisManager.PendingKind.TPA_ALL, player.getUniqueId());
        if (tpaAllPayload != null) {
            redis.removePending(RedisManager.PendingKind.TPA_ALL, player.getUniqueId());
            Location location = RedisManager.decodeLocationPayload(tpaAllPayload);
            if (location != null) {
                player.getScheduler().runDelayed(TryTpa.getInstance(),
                        task -> TeleportUtil.teleportImmediate(player, location),
                        null, JOIN_TELEPORT_DELAY_TICKS);
            }
        }
    }

    private static void scheduleTeleportToPlayer(Player player, UUID targetUUID) {
        if (targetUUID == null) return;
        // Runs on the joining player's region (Folia-safe).
        player.getScheduler().runDelayed(TryTpa.getInstance(), task -> {
            Player target = Bukkit.getPlayer(targetUUID);
            if (target == null) {
                player.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
                return;
            }
            // Snapshot target's location from any thread (Folia's Player#getLocation is safe).
            TeleportUtil.teleportImmediate(player, target.getLocation());
        }, null, JOIN_TELEPORT_DELAY_TICKS);
    }

    private static UUID parseUUID(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

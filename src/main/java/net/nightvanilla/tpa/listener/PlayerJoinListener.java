package net.nightvanilla.tpa.listener;

import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.redis.RedisManager;
import net.nightvanilla.tpa.util.MessageUtil;
import net.nightvanilla.tpa.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void handle(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        RedisManager redis = TryTpa.getInstance().getRedisManager();
        redis.registerPlayer(player.getUniqueId(), player.getName());

        if (!redis.isAvailable()) return;

        // Check if this player is arriving for a pending cross-server TPA (they teleport TO someone)
        UUID tpaTarget = redis.getPendingTpa(player.getUniqueId());
        if (tpaTarget != null) {
            redis.removePendingTpa(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(TryTpa.getInstance(), () -> {
                Player target = Bukkit.getPlayer(tpaTarget);
                if (target != null) {
                    TeleportUtil.teleport(player, target.getLocation());
                } else {
                    player.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
                }
            }, 5L);
            return;
        }

        // Check if this player is arriving for a pending cross-server TpaHere (they teleport TO the requester)
        UUID tpaHereRequester = redis.getPendingTpaHere(player.getUniqueId());
        if (tpaHereRequester != null) {
            redis.removePendingTpaHere(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(TryTpa.getInstance(), () -> {
                Player requester = Bukkit.getPlayer(tpaHereRequester);
                if (requester != null) {
                    TeleportUtil.teleport(player, requester.getLocation());
                } else {
                    player.sendMessage(MessageUtil.get("Messages.PlayerNotFound"));
                }
            }, 5L);
        }
    }

}

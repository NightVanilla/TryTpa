package net.nightvanilla.tpa.listener;

import net.nightvanilla.tpa.TryTpa;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void handle(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        TryTpa.getInstance().getRedisManager().registerPlayer(player.getUniqueId(), player.getName());
    }

}

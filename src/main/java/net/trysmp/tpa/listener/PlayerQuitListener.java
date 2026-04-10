package net.trysmp.tpa.listener;

import net.trysmp.tpa.TryTpa;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    @EventHandler
    public void handle(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        TryTpa.getInstance().getRequestStore().removeAllForPlayer(player.getUniqueId());
    }

}

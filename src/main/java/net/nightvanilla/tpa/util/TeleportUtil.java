package net.nightvanilla.tpa.util;

import lombok.experimental.UtilityClass;
import net.nightvanilla.tpa.TryTpa;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;

@UtilityClass
public class TeleportUtil {

    private static final HashMap<UUID, Location> move = new HashMap<>();

    /**
     * Teleports the player immediately without countdown or movement check.
     * Used for cross-server arrivals where the player just joined and should
     * be moved directly to the target location.
     */
    public static void teleportImmediate(Player player, Location location) {
        player.closeInventory();
        player.teleport(location);
        playSound(player, "Teleport.TeleportSound");
    }

    public static void teleport(Player player, Location location) {
        player.closeInventory();

        if (TryTpa.getInstance().getConfig().getInt("Teleport.CoolDown") < 1 || player.hasPermission("trytpa.bypass.teleport")) {
            player.teleport(location);
            playSound(player, "Teleport.TeleportSound");
            return;
        }

        move.put(player.getUniqueId(), player.getLocation());

        final int[] seconds = {6};
        UUID uuid = player.getUniqueId();
        new BukkitRunnable() {
            @Override
            public void run() {
                seconds[0]--;

                if (Bukkit.getPlayer(uuid) == null) {
                    move.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }

                if (TryTpa.getInstance().getConfig().getBoolean("Teleport.CancelOnMove")) {
                    Location moveLocation = move.get(player.getUniqueId());
                    if (moveLocation != null && moveLocation.distance(player.getLocation()) > TryTpa.getInstance().getConfig().getDouble("Teleport.MaximumMoveDistance")) {
                        player.sendMessage(MessageUtil.get("Teleport.CancelMessage"));
                        if (!(TryTpa.getInstance().getConfig().getString("Teleport.CancelTitle.Title").equalsIgnoreCase("")) || !(TryTpa.getInstance().getConfig().getString("Teleport.CancelTitle.SubTitle").equalsIgnoreCase(""))) {
                            player.sendTitle(MessageUtil.get("Teleport.CancelTitle.Title"), MessageUtil.get("Teleport.CancelTitle.SubTitle"));
                        }
                        playSound(player, "Teleport.CancelSound");
                        move.remove(player.getUniqueId());
                        this.cancel();
                        return;
                    }
                }

                switch (seconds[0]) {
                    case 5, 4, 3, 2, 1 -> {
                        if (!(TryTpa.getInstance().getConfig().getString("Teleport.Message").equalsIgnoreCase(""))) {
                            player.sendMessage(MessageUtil.get("Teleport.Message").replaceAll("%seconds%", String.valueOf(seconds[0])));
                        }
                        if (!(TryTpa.getInstance().getConfig().getString("Teleport.Actionbar").equalsIgnoreCase(""))) {
                            player.sendActionBar(MessageUtil.get("Teleport.Actionbar").replaceAll("%seconds%", String.valueOf(seconds[0])));
                        }
                        if (!(TryTpa.getInstance().getConfig().getString("Teleport.Title.Title").equalsIgnoreCase("")) || !(TryTpa.getInstance().getConfig().getString("Teleport.Title.SubTitle").equalsIgnoreCase(""))) {
                            player.sendTitle(MessageUtil.get("Teleport.Title.Title").replaceAll("%seconds%", String.valueOf(seconds[0])), MessageUtil.get("Teleport.Title.SubTitle").replaceAll("%seconds%", String.valueOf(seconds[0])));
                        }
                        playSound(player, "Teleport.CoolDownSound");
                    }
                    case 0 -> {
                        player.teleport(location);
                        playSound(player, "Teleport.TeleportSound");
                        move.remove(player.getUniqueId());
                        this.cancel();
                    }
                }
            }
        }.runTaskTimer(TryTpa.getInstance(), 0, 20);
    }

    private static void playSound(Player player, String key) {
        String sound = TryTpa.getInstance().getConfig().getString(key, "");
        if (!sound.equalsIgnoreCase("")) {
            try {
                player.playSound(player.getLocation(), Sound.valueOf(sound), 5, 5);
            } catch (Exception ignored) { }
        }
    }

}

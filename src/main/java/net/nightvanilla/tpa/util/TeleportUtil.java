package net.nightvanilla.tpa.util;

import lombok.experimental.UtilityClass;
import net.nightvanilla.tpa.TryTpa;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
public class TeleportUtil {

    private static final ConcurrentHashMap<UUID, Location> move = new ConcurrentHashMap<>();

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

        int cooldown = TryTpa.getInstance().getConfig().getInt("Teleport.CoolDown");
        if (cooldown < 1 || player.hasPermission("trytpa.bypass.teleport")) {
            player.teleport(location);
            playSound(player, "Teleport.TeleportSound");
            return;
        }

        UUID uuid = player.getUniqueId();
        move.put(uuid, player.getLocation());

        final int[] seconds = {cooldown + 1};
        new BukkitRunnable() {
            @Override
            public void run() {
                seconds[0]--;

                if (Bukkit.getPlayer(uuid) == null) {
                    move.remove(uuid);
                    this.cancel();
                    return;
                }

                if (TryTpa.getInstance().getConfig().getBoolean("Teleport.CancelOnMove")) {
                    Location moveLocation = move.get(uuid);
                    if (moveLocation != null && moveLocation.distance(player.getLocation()) > TryTpa.getInstance().getConfig().getDouble("Teleport.MaximumMoveDistance")) {
                        player.sendMessage(MessageUtil.get("Teleport.CancelMessage"));
                        String cancelTitle = TryTpa.getInstance().getConfig().getString("Teleport.CancelTitle.Title", "");
                        String cancelSubTitle = TryTpa.getInstance().getConfig().getString("Teleport.CancelTitle.SubTitle", "");
                        if (!cancelTitle.isEmpty() || !cancelSubTitle.isEmpty()) {
                            player.sendTitle(MessageUtil.get("Teleport.CancelTitle.Title"), MessageUtil.get("Teleport.CancelTitle.SubTitle"));
                        }
                        playSound(player, "Teleport.CancelSound");
                        move.remove(uuid);
                        this.cancel();
                        return;
                    }
                }

                if (seconds[0] > 0) {
                    String countdownStr = String.valueOf(seconds[0]);
                    if (!TryTpa.getInstance().getConfig().getString("Teleport.Message", "").isEmpty()) {
                        player.sendMessage(MessageUtil.get("Teleport.Message").replace("%seconds%", countdownStr));
                    }
                    if (!TryTpa.getInstance().getConfig().getString("Teleport.Actionbar", "").isEmpty()) {
                        player.sendActionBar(MessageUtil.get("Teleport.Actionbar").replace("%seconds%", countdownStr));
                    }
                    String title = TryTpa.getInstance().getConfig().getString("Teleport.Title.Title", "");
                    String subTitle = TryTpa.getInstance().getConfig().getString("Teleport.Title.SubTitle", "");
                    if (!title.isEmpty() || !subTitle.isEmpty()) {
                        player.sendTitle(
                                MessageUtil.get("Teleport.Title.Title").replace("%seconds%", countdownStr),
                                MessageUtil.get("Teleport.Title.SubTitle").replace("%seconds%", countdownStr)
                        );
                    }
                    playSound(player, "Teleport.CoolDownSound");
                } else {
                    player.teleport(location);
                    playSound(player, "Teleport.TeleportSound");
                    move.remove(uuid);
                    this.cancel();
                }
            }
        }.runTaskTimer(TryTpa.getInstance(), 0, 20);
    }

    private static void playSound(Player player, String key) {
        String sound = TryTpa.getInstance().getConfig().getString(key, "");
        if (!sound.isEmpty()) {
            try {
                player.playSound(player.getLocation(), Sound.valueOf(sound), 5, 5);
            } catch (IllegalArgumentException e) {
                TryTpa.getInstance().getLogger().warning("Unknown sound '" + sound + "' configured at " + key);
            }
        }
    }

}

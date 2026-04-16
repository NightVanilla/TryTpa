package net.nightvanilla.tpa.util;

import lombok.experimental.UtilityClass;
import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
public class TeleportUtil {

    private static final ConcurrentHashMap<UUID, Location> moveOrigins = new ConcurrentHashMap<>();

    /**
     * Teleports immediately, no countdown. Used for cross-server arrivals where
     * the player just joined and should move straight to the target location.
     */
    public static void teleportImmediate(Player player, Location location) {
        player.closeInventory();
        player.teleportAsync(location).thenRun(() -> playSound(player, "Teleport.TeleportSound"));
    }

    /** Teleport with configurable countdown and movement cancellation. Folia-safe. */
    public static void teleport(Player player, Location location) {
        player.closeInventory();

        Settings settings = TryTpa.getInstance().getSettings();
        int cooldown = settings.getTeleportCooldownSeconds();
        if (cooldown < 1 || player.hasPermission("trytpa.bypass.teleport")) {
            player.teleportAsync(location).thenRun(() -> playSound(player, "Teleport.TeleportSound"));
            return;
        }

        UUID uuid = player.getUniqueId();
        moveOrigins.put(uuid, player.getLocation());

        final int[] seconds = {cooldown + 1};

        // Entity scheduler runs on the player's region (Folia-safe). Initial delay must be >= 1.
        player.getScheduler().runAtFixedRate(TryTpa.getInstance(), task -> {
            seconds[0]--;

            if (Bukkit.getPlayer(uuid) == null) {
                moveOrigins.remove(uuid);
                task.cancel();
                return;
            }

            if (settings.isCancelOnMove() && movedTooFar(player, uuid, settings.getMaxMoveDistance())) {
                player.sendMessage(MessageUtil.get("Teleport.CancelMessage"));
                String cancelTitle = TryTpa.getInstance().getConfig().getString("Teleport.CancelTitle.Title", "");
                String cancelSubTitle = TryTpa.getInstance().getConfig().getString("Teleport.CancelTitle.SubTitle", "");
                if (!cancelTitle.isEmpty() || !cancelSubTitle.isEmpty()) {
                    player.sendTitle(MessageUtil.get("Teleport.CancelTitle.Title"),
                            MessageUtil.get("Teleport.CancelTitle.SubTitle"));
                }
                playSound(player, "Teleport.CancelSound");
                moveOrigins.remove(uuid);
                task.cancel();
                return;
            }

            if (seconds[0] > 0) {
                sendCountdown(player, seconds[0]);
                playSound(player, "Teleport.CoolDownSound");
            } else {
                player.teleportAsync(location).thenRun(() -> playSound(player, "Teleport.TeleportSound"));
                moveOrigins.remove(uuid);
                task.cancel();
            }
        }, () -> moveOrigins.remove(uuid), 1L, 20L);
    }

    private static boolean movedTooFar(Player player, UUID uuid, double maxDistance) {
        Location origin = moveOrigins.get(uuid);
        if (origin == null) return false;
        Location current = player.getLocation();
        // distance() throws if worlds differ — compare worlds first.
        if (!origin.getWorld().equals(current.getWorld())) return true;
        return origin.distance(current) > maxDistance;
    }

    private static void sendCountdown(Player player, int seconds) {
        String countdown = String.valueOf(seconds);
        String message = TryTpa.getInstance().getConfig().getString("Teleport.Message", "");
        if (!message.isEmpty()) {
            player.sendMessage(MessageUtil.get("Teleport.Message").replace("%seconds%", countdown));
        }
        String actionbar = TryTpa.getInstance().getConfig().getString("Teleport.Actionbar", "");
        if (!actionbar.isEmpty()) {
            player.sendActionBar(MessageUtil.get("Teleport.Actionbar").replace("%seconds%", countdown));
        }
        String title = TryTpa.getInstance().getConfig().getString("Teleport.Title.Title", "");
        String subTitle = TryTpa.getInstance().getConfig().getString("Teleport.Title.SubTitle", "");
        if (!title.isEmpty() || !subTitle.isEmpty()) {
            player.sendTitle(
                    MessageUtil.get("Teleport.Title.Title").replace("%seconds%", countdown),
                    MessageUtil.get("Teleport.Title.SubTitle").replace("%seconds%", countdown)
            );
        }
    }

    private static void playSound(Player player, String key) {
        String sound = TryTpa.getInstance().getConfig().getString(key, "");
        if (sound.isEmpty()) return;
        try {
            player.playSound(player.getLocation(), Sound.valueOf(sound), 5, 5);
        } catch (IllegalArgumentException e) {
            TryTpa.getInstance().getLogger().warning("Unknown sound '" + sound + "' configured at " + key);
        }
    }
}

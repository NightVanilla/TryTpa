package net.nightvanilla.tpa.service;

import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.config.Settings;
import net.nightvanilla.tpa.request.RequestType;
import net.nightvanilla.tpa.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Handles cross-server pub/sub payloads. Always dispatches to the global region
 * scheduler so handlers run on a server-thread in a Folia-safe way.
 */
public final class PubSubHandler implements BiConsumer<UUID, String> {

    private final TryTpa plugin;
    private final Settings settings;
    private final TpaService service;

    public PubSubHandler(TryTpa plugin, Settings settings, TpaService service) {
        this.plugin = plugin;
        this.settings = settings;
        this.service = service;
    }

    @Override
    public void accept(UUID uuid, String message) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> dispatch(uuid, message));
    }

    private void dispatch(UUID uuid, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        String[] parts = message.split(":", 3);
        if (parts.length < 2) return;
        String type = parts[0];
        String payload = parts[1];

        switch (type) {
            case "TPA" -> notifyRequest(player, RequestType.TPA, payload);
            case "TPAHERE" -> notifyRequest(player, RequestType.TPA_HERE, payload);
            case "TPAALL" -> notifyRequest(player, RequestType.TPA_ALL, payload);
            case "TPA_CONNECT" -> {
                String targetServer = payload;
                String acceptorName = parts.length > 2 ? parts[2] : "";
                if (!acceptorName.isEmpty()) {
                    player.sendMessage(MessageUtil.get("Messages.AcceptedOther").replace("%player%", acceptorName));
                }
                service.connectPlayerToServer(player, targetServer);
            }
            case "TPAHERE_ACCEPTED" -> {
                String acceptorName = payload;
                player.sendMessage(MessageUtil.get("Messages.AcceptedOther").replace("%player%", acceptorName));
            }
        }
    }

    private void notifyRequest(Player player, RequestType type, String senderName) {
        player.sendMessage(MessageUtil.getRequest(type.getConfigKey(), senderName));
        boolean enabled = switch (type) {
            case TPA -> settings.isTpaSound();
            case TPA_HERE -> settings.isTpaHereSound();
            case TPA_ALL -> settings.isTpaAllSound();
        };
        if (enabled) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 5, 5);
        }
    }
}

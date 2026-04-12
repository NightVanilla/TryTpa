package net.nightvanilla.tpa;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import lombok.Getter;
import net.nightvanilla.tpa.command.*;
import net.nightvanilla.tpa.listener.PlayerJoinListener;
import net.nightvanilla.tpa.listener.PlayerQuitListener;
import net.nightvanilla.tpa.redis.RedisManager;
import net.nightvanilla.tpa.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

@Getter
public class TryTpa extends JavaPlugin {

    @Getter
    private static TryTpa instance;

    private RedisManager redisManager;

    private RequestStore requestStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;

        redisManager = new RedisManager();
        requestStore = new RequestStore(redisManager);

        // Refresh the cross-server player name cache every 5 seconds on an async thread.
        // Tab completion reads from this cache so it never blocks the main thread on Redis I/O.
        Bukkit.getAsyncScheduler().runAtFixedRate(this,
                task -> redisManager.refreshPlayerNamesCache(),
                0, 5, TimeUnit.SECONDS);

        new RemoveTpaAllCommand();
        new TpaCommand();
        new TpaAcceptCommand();
        new TpaHereCommand();
        new TpaHereAcceptCommand();
        new TpaAllCommand();
        new TpaToggleCommand();
        new TpaHereToggleCommand();
        new TpaAllToggleCommand();

        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        redisManager.startSubscriber((uuid, message) -> {
            // Deliver cross-server TPA notifications on the global region scheduler (Folia-safe)
            Bukkit.getGlobalRegionScheduler().run(this, task -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) return;

                String[] parts = message.split(":", 3);
                if (parts.length < 2) return;

                String type = parts[0];
                String senderName = parts[1];

                switch (type) {
                    case "TPA" -> {
                        player.sendMessage(MessageUtil.getRequest("Tpa", senderName));
                        if (getConfig().getBoolean("Settings.Sounds.Tpa")) {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 5, 5);
                        }
                    }
                    case "TPAHERE" -> {
                        player.sendMessage(MessageUtil.getRequest("TpaHere", senderName));
                        if (getConfig().getBoolean("Settings.Sounds.TpaHere")) {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 5, 5);
                        }
                    }
                    case "TPAALL" -> {
                        player.sendMessage(MessageUtil.getRequest("TpaAll", senderName));
                        if (getConfig().getBoolean("Settings.Sounds.TpaAll")) {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 5, 5);
                        }
                    }
                    case "TPA_CONNECT" -> {
                        // senderName = target server, parts[2] = acceptor's name
                        String targetServer = senderName;
                        String acceptorName = parts.length > 2 ? parts[2] : "";
                        if (!acceptorName.isEmpty()) {
                            player.sendMessage(MessageUtil.get("Messages.AcceptedOther").replace("%player%", acceptorName));
                        }
                        connectPlayerToServer(player, targetServer);
                    }
                    case "TPAHERE_ACCEPTED" -> {
                        // senderName = acceptor's name
                        player.sendMessage(MessageUtil.get("Messages.AcceptedOther").replace("%player%", senderName));
                    }
                }
            });
        });
    }

    public void connectPlayerToServer(Player player, String serverName) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
        } catch (Exception e) {
            getLogger().warning("Failed to connect " + player.getName() + " to " + serverName + ": " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (redisManager != null) {
            redisManager.close();
        }
    }

}

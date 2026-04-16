package net.nightvanilla.tpa;

import lombok.Getter;
import net.nightvanilla.tpa.command.RemoveTpaAllCommand;
import net.nightvanilla.tpa.command.TpaAcceptCommand;
import net.nightvanilla.tpa.command.TpaAllCommand;
import net.nightvanilla.tpa.command.TpaCommand;
import net.nightvanilla.tpa.command.TpaHereAcceptCommand;
import net.nightvanilla.tpa.command.TpaHereCommand;
import net.nightvanilla.tpa.command.TpaToggleCommand;
import net.nightvanilla.tpa.config.Settings;
import net.nightvanilla.tpa.listener.PlayerJoinListener;
import net.nightvanilla.tpa.listener.PlayerQuitListener;
import net.nightvanilla.tpa.redis.RedisManager;
import net.nightvanilla.tpa.request.RequestStore;
import net.nightvanilla.tpa.service.PubSubHandler;
import net.nightvanilla.tpa.service.TpaService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

@Getter
public final class TryTpa extends JavaPlugin {

    @Getter
    private static TryTpa instance;

    private Settings settings;
    private RedisManager redisManager;
    private RequestStore requestStore;
    private TpaService tpaService;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.settings = new Settings(getConfig());
        this.redisManager = new RedisManager(settings);
        this.requestStore = new RequestStore(redisManager);
        this.tpaService = new TpaService(this, settings, requestStore, redisManager);

        registerCommands();
        registerListeners();
        registerPluginChannels();
        startCaches();
        startPubSub();
    }

    @Override
    public void onDisable() {
        if (redisManager != null) redisManager.close();
    }

    /** Delegates Bungee-Connect plugin messages to {@link TpaService}. */
    public void connectPlayerToServer(org.bukkit.entity.Player player, String serverName) {
        tpaService.connectPlayerToServer(player, serverName);
    }

    // ---------------------------------------------------------------------

    private void registerCommands() {
        new RemoveTpaAllCommand();
        new TpaCommand();
        new TpaAcceptCommand();
        new TpaHereCommand();
        new TpaHereAcceptCommand();
        new TpaAllCommand();
        TpaToggleCommand.registerAll();
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), this);
    }

    private void registerPluginChannels() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }

    /** Keeps the online-player name cache warm for tab completion. */
    private void startCaches() {
        if (!redisManager.isAvailable()) return;
        // Folia's async scheduler requires initialDelay > 0
        Bukkit.getAsyncScheduler().runAtFixedRate(this,
                task -> redisManager.refreshPlayerNamesCache(),
                1L, 5L, TimeUnit.SECONDS);
    }

    private void startPubSub() {
        PubSubHandler handler = new PubSubHandler(this, settings, tpaService);
        redisManager.startSubscriber(handler);
    }
}

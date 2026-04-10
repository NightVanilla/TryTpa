package net.trysmp.tpa;

import lombok.Getter;
import net.trysmp.tpa.command.*;
import net.trysmp.tpa.listener.PlayerQuitListener;
import net.trysmp.tpa.redis.RedisManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class TryTpa extends JavaPlugin {

    @Getter
    private static TryTpa instance;

    @Getter
    private RedisManager redisManager;

    @Getter
    private RequestStore requestStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;

        redisManager = new RedisManager();
        requestStore = new RequestStore(redisManager);

        new RemoveTpaAllCommand();
        new TpaCommand();
        new TpaAcceptCommand();
        new TpaHereCommand();
        new TpaHereAcceptCommand();
        new TpaAllCommand();

        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), this);
    }

    @Override
    public void onDisable() {
        if (redisManager != null) {
            redisManager.close();
        }
    }

}

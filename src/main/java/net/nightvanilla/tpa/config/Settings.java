package net.nightvanilla.tpa.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Snapshot of frequently-read config values, loaded once on enable/reload.
 * Keeps hot paths (commands, listeners) out of the config map.
 */
@Getter
public final class Settings {

    private final long tpaExpirationSeconds;
    private final long tpaHereExpirationSeconds;
    private final long tpaAllExpirationSeconds;

    private final long tpaCooldownMillis;
    private final long tpaHereCooldownMillis;
    private final long tpaAllCooldownMillis;

    private final boolean tpaSound;
    private final boolean tpaHereSound;
    private final boolean tpaAllSound;

    private final String serverName;

    private final boolean redisEnabled;
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;
    private final int redisDatabase;

    private final int teleportCooldownSeconds;
    private final boolean cancelOnMove;
    private final double maxMoveDistance;

    public Settings(FileConfiguration c) {
        this.tpaExpirationSeconds = c.getLong("Settings.Expiration.Tpa");
        this.tpaHereExpirationSeconds = c.getLong("Settings.Expiration.TpaHere");
        this.tpaAllExpirationSeconds = c.getLong("Settings.Expiration.TpaAll");

        this.tpaCooldownMillis = c.getLong("Settings.Cooldown.Tpa");
        this.tpaHereCooldownMillis = c.getLong("Settings.Cooldown.TpaHere");
        this.tpaAllCooldownMillis = c.getLong("Settings.Cooldown.TpaAll");

        this.tpaSound = c.getBoolean("Settings.Sounds.Tpa");
        this.tpaHereSound = c.getBoolean("Settings.Sounds.TpaHere");
        this.tpaAllSound = c.getBoolean("Settings.Sounds.TpaAll");

        this.serverName = c.getString("Server.Name", "");

        this.redisEnabled = c.getBoolean("Redis.Enabled", false);
        this.redisHost = c.getString("Redis.Host", "localhost");
        this.redisPort = c.getInt("Redis.Port", 6379);
        this.redisPassword = c.getString("Redis.Password", "");
        this.redisDatabase = c.getInt("Redis.Database", 0);

        this.teleportCooldownSeconds = c.getInt("Teleport.CoolDown");
        this.cancelOnMove = c.getBoolean("Teleport.CancelOnMove");
        this.maxMoveDistance = c.getDouble("Teleport.MaximumMoveDistance");
    }
}

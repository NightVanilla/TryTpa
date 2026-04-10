package net.trysmp.tpa.redis;

import net.trysmp.tpa.TryTpa;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class RedisManager {

    private static final String KEY_PREFIX = "trytpa:";

    private final boolean enabled;
    private JedisPool pool;

    public RedisManager() {
        this.enabled = TryTpa.getInstance().getConfig().getBoolean("Redis.Enabled", false);
        if (!enabled) return;

        String host = TryTpa.getInstance().getConfig().getString("Redis.Host", "localhost");
        int port = TryTpa.getInstance().getConfig().getInt("Redis.Port", 6379);
        String password = TryTpa.getInstance().getConfig().getString("Redis.Password", "");
        int database = TryTpa.getInstance().getConfig().getInt("Redis.Database", 0);

        try {
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(16);
            config.setMaxIdle(8);
            config.setMinIdle(2);
            config.setTestOnBorrow(true);
            config.setTestOnReturn(true);

            if (password == null || password.isEmpty()) {
                pool = new JedisPool(config, host, port, 2000, null, database);
            } else {
                pool = new JedisPool(config, host, port, 2000, password, database);
            }

            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }

            TryTpa.getInstance().getLogger().info("Connected to Redis at " + host + ":" + port);
        } catch (Exception e) {
            TryTpa.getInstance().getLogger().log(Level.SEVERE, "Failed to connect to Redis: " + e.getMessage());
            pool = null;
        }
    }

    public boolean isAvailable() {
        return enabled && pool != null && !pool.isClosed();
    }

    // ---- Requests ----

    public void setRequest(String type, UUID requester, UUID target, long ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(reqKey(type, requester), ttlSeconds, target.toString());
        } catch (Exception e) {
            log("setRequest", e);
        }
    }

    public UUID getRequest(String type, UUID requester) {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(reqKey(type, requester));
            return value != null ? UUID.fromString(value) : null;
        } catch (Exception e) {
            log("getRequest", e);
            return null;
        }
    }

    public void removeRequest(String type, UUID requester) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(reqKey(type, requester));
        } catch (Exception e) {
            log("removeRequest", e);
        }
    }

    public Set<UUID> getRequestersForTarget(String type, UUID target) {
        Set<UUID> result = new HashSet<>();
        try (Jedis jedis = pool.getResource()) {
            String pattern = KEY_PREFIX + type + ":req:*";
            String cursor = "0";
            ScanParams params = new ScanParams().match(pattern).count(100);
            do {
                ScanResult<String> scan = jedis.scan(cursor, params);
                cursor = scan.getCursor();
                for (String key : scan.getResult()) {
                    String value = jedis.get(key);
                    if (target.toString().equals(value)) {
                        result.add(UUID.fromString(key.substring((KEY_PREFIX + type + ":req:").length())));
                    }
                }
            } while (!cursor.equals("0"));
        } catch (Exception e) {
            log("getRequestersForTarget", e);
        }
        return result;
    }

    // ---- TpaAll location ----

    public void setTpaAllRequest(UUID requester, Location location, long ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            String value = location.getWorld().getName() + ":"
                    + location.getX() + ":" + location.getY() + ":" + location.getZ() + ":"
                    + location.getYaw() + ":" + location.getPitch();
            jedis.setex(KEY_PREFIX + "tpaall:req:" + requester, ttlSeconds, value);
        } catch (Exception e) {
            log("setTpaAllRequest", e);
        }
    }

    public Location getTpaAllRequest(UUID requester) {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(KEY_PREFIX + "tpaall:req:" + requester);
            if (value == null) return null;
            String[] parts = value.split(":");
            if (parts.length != 6) return null;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            return new Location(world,
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (Exception e) {
            log("getTpaAllRequest", e);
            return null;
        }
    }

    public void removeTpaAllRequest(UUID requester) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(KEY_PREFIX + "tpaall:req:" + requester);
        } catch (Exception e) {
            log("removeTpaAllRequest", e);
        }
    }

    // ---- Cooldowns ----

    public void setCooldown(String type, UUID player, long expiryMillis) {
        try (Jedis jedis = pool.getResource()) {
            long ttl = Math.max(1, (expiryMillis - System.currentTimeMillis()) / 1000 + 1);
            jedis.setex(cdKey(type, player), ttl, String.valueOf(expiryMillis));
        } catch (Exception e) {
            log("setCooldown", e);
        }
    }

    public long getCooldown(String type, UUID player) {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(cdKey(type, player));
            return value != null ? Long.parseLong(value) : 0L;
        } catch (Exception e) {
            log("getCooldown", e);
            return 0L;
        }
    }

    // ---- Player cleanup ----

    public void cleanupPlayer(UUID player) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(
                    reqKey("tpa", player),
                    reqKey("tpahere", player),
                    KEY_PREFIX + "tpaall:req:" + player,
                    cdKey("tpa", player),
                    cdKey("tpahere", player),
                    cdKey("tpaall", player)
            );
        } catch (Exception e) {
            log("cleanupPlayer", e);
        }
    }

    public void close() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    // ---- Helpers ----

    private String reqKey(String type, UUID uuid) {
        return KEY_PREFIX + type + ":req:" + uuid;
    }

    private String cdKey(String type, UUID uuid) {
        return KEY_PREFIX + type + ":cd:" + uuid;
    }

    private void log(String method, Exception e) {
        TryTpa.getInstance().getLogger().log(Level.WARNING, "[Redis] Error in " + method + ": " + e.getMessage());
    }

}

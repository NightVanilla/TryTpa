package net.nightvanilla.tpa.redis;

import net.nightvanilla.tpa.TryTpa;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public class RedisManager {

    private static final String KEY_PREFIX = "trytpa:";
    private static final String ONLINE_PREFIX = KEY_PREFIX + "online:";
    private static final String NAMES_PREFIX = KEY_PREFIX + "playernames:";
    private static final String MSG_CHANNEL_PREFIX = KEY_PREFIX + "msg:";

    private final boolean enabled;
    private JedisPool pool;

    private Thread subscriberThread;
    private volatile JedisPubSub activePubSub;

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

    public Set<UUID> getTpaAllRequesters() {
        Set<UUID> result = new HashSet<>();
        try (Jedis jedis = pool.getResource()) {
            String cursor = "0";
            ScanParams params = new ScanParams().match(KEY_PREFIX + "tpaall:req:*").count(100);
            do {
                ScanResult<String> scan = jedis.scan(cursor, params);
                cursor = scan.getCursor();
                for (String key : scan.getResult()) {
                    result.add(UUID.fromString(key.substring((KEY_PREFIX + "tpaall:req:").length())));
                }
            } while (!cursor.equals("0"));
        } catch (Exception e) {
            log("getTpaAllRequesters", e);
        }
        return result;
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

    // ---- Player registry ----

    public void registerPlayer(UUID uuid, String name) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(ONLINE_PREFIX + uuid, 86400, name);
            jedis.setex(NAMES_PREFIX + name.toLowerCase(), 86400, uuid.toString());
        } catch (Exception e) {
            log("registerPlayer", e);
        }
    }

    public void unregisterPlayer(UUID uuid, String name) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(ONLINE_PREFIX + uuid, NAMES_PREFIX + name.toLowerCase());
        } catch (Exception e) {
            log("unregisterPlayer", e);
        }
    }

    public String getPlayerName(UUID uuid) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(ONLINE_PREFIX + uuid);
        } catch (Exception e) {
            log("getPlayerName", e);
            return null;
        }
    }

    public UUID getPlayerUUID(String name) {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(NAMES_PREFIX + name.toLowerCase());
            return value != null ? UUID.fromString(value) : null;
        } catch (Exception e) {
            log("getPlayerUUID", e);
            return null;
        }
    }

    public Set<String> getOnlinePlayerNames() {
        Set<String> names = new HashSet<>();
        try (Jedis jedis = pool.getResource()) {
            String cursor = "0";
            ScanParams params = new ScanParams().match(ONLINE_PREFIX + "*").count(100);
            do {
                ScanResult<String> scan = jedis.scan(cursor, params);
                cursor = scan.getCursor();
                for (String key : scan.getResult()) {
                    String name = jedis.get(key);
                    if (name != null) names.add(name);
                }
            } while (!cursor.equals("0"));
        } catch (Exception e) {
            log("getOnlinePlayerNames", e);
        }
        return names;
    }

    // ---- Request origin server name ----

    public void setRequestServerName(String type, UUID requester, String serverName, long ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(KEY_PREFIX + type + ":req:" + requester + ":server", ttlSeconds, serverName);
        } catch (Exception e) {
            log("setRequestServerName", e);
        }
    }

    public String getRequestServerName(String type, UUID requester) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(KEY_PREFIX + type + ":req:" + requester + ":server");
        } catch (Exception e) {
            log("getRequestServerName", e);
            return null;
        }
    }

    public void removeRequestServerName(String type, UUID requester) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(KEY_PREFIX + type + ":req:" + requester + ":server");
        } catch (Exception e) {
            log("removeRequestServerName", e);
        }
    }

    // ---- Pending cross-server teleports ----

    /** After connecting to the target server, player should teleport to targetUUID */
    public void setPendingTpa(UUID player, UUID target, long ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(KEY_PREFIX + "pendingtpa:" + player, ttlSeconds, target.toString());
        } catch (Exception e) {
            log("setPendingTpa", e);
        }
    }

    public UUID getPendingTpa(UUID player) {
        try (Jedis jedis = pool.getResource()) {
            String val = jedis.get(KEY_PREFIX + "pendingtpa:" + player);
            return val != null ? UUID.fromString(val) : null;
        } catch (Exception e) {
            log("getPendingTpa", e);
            return null;
        }
    }

    public void removePendingTpa(UUID player) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(KEY_PREFIX + "pendingtpa:" + player);
        } catch (Exception e) {
            log("removePendingTpa", e);
        }
    }

    /** After connecting to the requester's server, player should teleport to requesterUUID */
    public void setPendingTpaHere(UUID player, UUID requester, long ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(KEY_PREFIX + "pendingtpahere:" + player, ttlSeconds, requester.toString());
        } catch (Exception e) {
            log("setPendingTpaHere", e);
        }
    }

    public UUID getPendingTpaHere(UUID player) {
        try (Jedis jedis = pool.getResource()) {
            String val = jedis.get(KEY_PREFIX + "pendingtpahere:" + player);
            return val != null ? UUID.fromString(val) : null;
        } catch (Exception e) {
            log("getPendingTpaHere", e);
            return null;
        }
    }

    public void removePendingTpaHere(UUID player) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(KEY_PREFIX + "pendingtpahere:" + player);
        } catch (Exception e) {
            log("removePendingTpaHere", e);
        }
    }

    // ---- Cross-server messaging via pub/sub ----

    public void publishToPlayer(UUID targetUUID, String message) {
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(MSG_CHANNEL_PREFIX + targetUUID, message);
        } catch (Exception e) {
            log("publishToPlayer", e);
        }
    }

    public void startSubscriber(BiConsumer<UUID, String> handler) {
        if (!isAvailable()) return;
        activePubSub = new JedisPubSub() {
            @Override
            public void onPMessage(String pattern, String channel, String message) {
                try {
                    UUID uuid = UUID.fromString(channel.substring(MSG_CHANNEL_PREFIX.length()));
                    handler.accept(uuid, message);
                } catch (Exception ignored) {}
            }
        };
        subscriberThread = new Thread(() -> {
            Jedis jedis = pool.getResource();
            try {
                jedis.psubscribe(activePubSub, MSG_CHANNEL_PREFIX + "*");
            } catch (Exception e) {
                if (isAvailable()) log("subscriber", e);
            } finally {
                try { jedis.close(); } catch (Exception ignored) {}
            }
        }, "TryTpa-Redis-Sub");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    public void stopSubscriber() {
        if (activePubSub != null) {
            try { activePubSub.punsubscribe(); } catch (Exception ignored) {}
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
        stopSubscriber();
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

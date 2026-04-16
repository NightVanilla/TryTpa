package net.nightvanilla.tpa.redis;

import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.config.Settings;
import net.nightvanilla.tpa.request.RequestType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Thin wrapper over Jedis providing the Redis operations TryTpa needs.
 * <p>
 * All blocking calls guard against I/O errors and unavailable pools.
 * The pub/sub subscriber runs on a dedicated connection with auto-reconnect.
 */
public final class RedisManager {

    private static final String PREFIX = "trytpa:";
    private static final String ONLINE_PREFIX = PREFIX + "online:";
    private static final String NAMES_PREFIX = PREFIX + "playernames:";
    private static final String MSG_CHANNEL_PREFIX = PREFIX + "msg:";
    private static final String PENDING_PREFIX = PREFIX + "pending:";
    private static final String TOGGLE_PREFIX = PREFIX + "toggle:";

    /** TTL for the online-player registry entries (24h). Refreshed on every join. */
    private static final int ONLINE_TTL_SECONDS = 86_400;

    /** Scan page size. */
    private static final int SCAN_COUNT = 200;

    private final boolean enabled;
    private JedisPool pool;

    private volatile Thread subscriberThread;
    private volatile Jedis subscriberConnection;
    private volatile JedisPubSub activePubSub;
    private volatile boolean subscriberShutdown;

    private volatile Set<String> cachedPlayerNames = Collections.emptySet();

    public RedisManager(Settings settings) {
        this.enabled = settings.isRedisEnabled();
        if (!enabled) return;

        try {
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(16);
            config.setMaxIdle(8);
            config.setMinIdle(2);
            config.setTestOnBorrow(true);
            config.setTestWhileIdle(true);

            String password = settings.getRedisPassword();
            this.pool = new JedisPool(
                    config,
                    settings.getRedisHost(),
                    settings.getRedisPort(),
                    2000,
                    (password == null || password.isEmpty()) ? null : password,
                    settings.getRedisDatabase()
            );

            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }

            TryTpa.getInstance().getLogger().info("Connected to Redis at "
                    + settings.getRedisHost() + ":" + settings.getRedisPort());
        } catch (Exception e) {
            TryTpa.getInstance().getLogger().log(Level.SEVERE, "Failed to connect to Redis: " + e.getMessage());
            this.pool = null;
        }
    }

    public boolean isAvailable() {
        return enabled && pool != null && !pool.isClosed();
    }

    // ---------------------------------------------------------------------
    // Request state
    // ---------------------------------------------------------------------

    public void setRequest(RequestType type, UUID requester, UUID target, long ttlSeconds, String serverName) {
        withJedis("setRequest", jedis -> {
            Pipeline p = jedis.pipelined();
            p.setex(reqKey(type, requester), ttlSeconds, target.toString());
            if (serverName != null && !serverName.isEmpty()) {
                p.setex(reqServerKey(type, requester), ttlSeconds, serverName);
            }
            p.sync();
            return null;
        });
    }

    public UUID getRequest(RequestType type, UUID requester) {
        String value = withJedis("getRequest", jedis -> jedis.get(reqKey(type, requester)));
        try {
            return value != null ? UUID.fromString(value) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void removeRequest(RequestType type, UUID requester) {
        withJedis("removeRequest", jedis -> jedis.del(reqKey(type, requester), reqServerKey(type, requester)));
    }

    /**
     * Returns the UUIDs of all players who have an outstanding request of the
     * given type aimed at {@code target}. Uses pipelining to batch GETs.
     */
    public Set<UUID> getRequestersForTarget(RequestType type, UUID target) {
        Set<UUID> result = new HashSet<>();
        withJedis("getRequestersForTarget", jedis -> {
            String pattern = PREFIX + type.getKey() + ":req:*";
            String cursor = "0";
            ScanParams params = new ScanParams().match(pattern).count(SCAN_COUNT);
            do {
                ScanResult<String> scan = jedis.scan(cursor, params);
                cursor = scan.getCursor();

                List<String> reqKeys = new ArrayList<>();
                for (String key : scan.getResult()) {
                    if (key.endsWith(":server")) continue;
                    reqKeys.add(key);
                }
                if (reqKeys.isEmpty()) continue;

                Pipeline p = jedis.pipelined();
                List<Response<String>> responses = new ArrayList<>(reqKeys.size());
                for (String key : reqKeys) responses.add(p.get(key));
                p.sync();

                String prefix = PREFIX + type.getKey() + ":req:";
                for (int i = 0; i < reqKeys.size(); i++) {
                    String value = responses.get(i).get();
                    if (target.toString().equals(value)) {
                        try {
                            result.add(UUID.fromString(reqKeys.get(i).substring(prefix.length())));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            } while (!"0".equals(cursor));
            return null;
        });
        return result;
    }

    public String getRequestServerName(RequestType type, UUID requester) {
        return withJedis("getRequestServerName", jedis -> jedis.get(reqServerKey(type, requester)));
    }

    // ---------------------------------------------------------------------
    // TpaAll — stored as serverName|world:x:y:z:yaw:pitch
    // ---------------------------------------------------------------------

    public void setTpaAllRequest(UUID requester, Location location, String serverName, long ttlSeconds) {
        String value = encodeLocationWithServer(location, serverName);
        withJedis("setTpaAllRequest", jedis -> jedis.setex(tpaAllKey(requester), ttlSeconds, value));
    }

    /** Returns a Location if the stored world exists on this server, {@code null} otherwise. */
    public Location getTpaAllLocation(UUID requester) {
        String raw = withJedis("getTpaAllLocation", jedis -> jedis.get(tpaAllKey(requester)));
        return decodeLocation(raw);
    }

    public String getTpaAllServerName(UUID requester) {
        String raw = withJedis("getTpaAllServerName", jedis -> jedis.get(tpaAllKey(requester)));
        return decodeServerName(raw);
    }

    public void removeTpaAllRequest(UUID requester) {
        withJedis("removeTpaAllRequest", jedis -> jedis.del(tpaAllKey(requester)));
    }

    public Set<UUID> getTpaAllRequesters() {
        Set<UUID> result = new HashSet<>();
        withJedis("getTpaAllRequesters", jedis -> {
            String pattern = PREFIX + "tpaall:req:*";
            String cursor = "0";
            ScanParams params = new ScanParams().match(pattern).count(SCAN_COUNT);
            do {
                ScanResult<String> scan = jedis.scan(cursor, params);
                cursor = scan.getCursor();
                for (String key : scan.getResult()) {
                    try {
                        result.add(UUID.fromString(key.substring((PREFIX + "tpaall:req:").length())));
                    } catch (IllegalArgumentException ignored) {}
                }
            } while (!"0".equals(cursor));
            return null;
        });
        return result;
    }

    // ---------------------------------------------------------------------
    // Cooldowns
    // ---------------------------------------------------------------------

    public void setCooldown(RequestType type, UUID player, long expiryMillis) {
        long ttl = Math.max(1L, (expiryMillis - System.currentTimeMillis()) / 1000L + 1L);
        withJedis("setCooldown", jedis -> jedis.setex(cdKey(type, player), ttl, String.valueOf(expiryMillis)));
    }

    public long getCooldown(RequestType type, UUID player) {
        String value = withJedis("getCooldown", jedis -> jedis.get(cdKey(type, player)));
        try {
            return value != null ? Long.parseLong(value) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // ---------------------------------------------------------------------
    // Player registry
    // ---------------------------------------------------------------------

    public void registerPlayer(UUID uuid, String name) {
        withJedis("registerPlayer", jedis -> {
            Pipeline p = jedis.pipelined();
            p.setex(ONLINE_PREFIX + uuid, ONLINE_TTL_SECONDS, name);
            p.setex(NAMES_PREFIX + name.toLowerCase(), ONLINE_TTL_SECONDS, uuid.toString());
            p.sync();
            return null;
        });
    }

    public void unregisterPlayer(UUID uuid, String name) {
        withJedis("unregisterPlayer", jedis -> jedis.del(ONLINE_PREFIX + uuid, NAMES_PREFIX + name.toLowerCase()));
    }

    public String getPlayerName(UUID uuid) {
        return withJedis("getPlayerName", jedis -> jedis.get(ONLINE_PREFIX + uuid));
    }

    public UUID getPlayerUUID(String name) {
        String value = withJedis("getPlayerUUID", jedis -> jedis.get(NAMES_PREFIX + name.toLowerCase()));
        try {
            return value != null ? UUID.fromString(value) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Blocking I/O — never call from the main thread.
     * Use {@link #getCachedPlayerNames()} for main-thread access.
     */
    public Set<String> getOnlinePlayerNames() {
        Set<String> names = new HashSet<>();
        withJedis("getOnlinePlayerNames", jedis -> {
            String cursor = "0";
            ScanParams params = new ScanParams().match(ONLINE_PREFIX + "*").count(SCAN_COUNT);
            do {
                ScanResult<String> scan = jedis.scan(cursor, params);
                cursor = scan.getCursor();

                if (scan.getResult().isEmpty()) continue;
                Pipeline p = jedis.pipelined();
                List<Response<String>> responses = new ArrayList<>(scan.getResult().size());
                for (String key : scan.getResult()) responses.add(p.get(key));
                p.sync();

                for (Response<String> r : responses) {
                    String n = r.get();
                    if (n != null) names.add(n);
                }
            } while (!"0".equals(cursor));
            return null;
        });
        return names;
    }

    public void refreshPlayerNamesCache() {
        if (!isAvailable()) return;
        cachedPlayerNames = Collections.unmodifiableSet(getOnlinePlayerNames());
    }

    public Set<String> getCachedPlayerNames() {
        return cachedPlayerNames;
    }

    // ---------------------------------------------------------------------
    // Pending cross-server teleports
    // ---------------------------------------------------------------------

    public enum PendingKind { TPA, TPA_HERE, TPA_ALL }

    public void setPending(PendingKind kind, UUID player, String payload, long ttlSeconds) {
        withJedis("setPending", jedis -> jedis.setex(pendingKey(kind, player), ttlSeconds, payload));
    }

    public String getPending(PendingKind kind, UUID player) {
        return withJedis("getPending", jedis -> jedis.get(pendingKey(kind, player)));
    }

    public void removePending(PendingKind kind, UUID player) {
        withJedis("removePending", jedis -> jedis.del(pendingKey(kind, player)));
    }

    // ---------------------------------------------------------------------
    // Toggle state (persistent; never wiped on quit)
    // ---------------------------------------------------------------------

    public void setToggle(RequestType type, UUID player, boolean disabled) {
        String key = toggleKey(type, player);
        withJedis("setToggle", jedis -> {
            if (disabled) jedis.set(key, "1");
            else jedis.del(key);
            return null;
        });
    }

    public boolean isToggled(RequestType type, UUID player) {
        Boolean b = withJedis("isToggled", jedis -> jedis.exists(toggleKey(type, player)));
        return Boolean.TRUE.equals(b);
    }

    // ---------------------------------------------------------------------
    // Cross-server messaging
    // ---------------------------------------------------------------------

    public void publishToPlayer(UUID targetUUID, String message) {
        withJedis("publishToPlayer", jedis -> jedis.publish(MSG_CHANNEL_PREFIX + targetUUID, message));
    }

    public void startSubscriber(BiConsumer<UUID, String> handler) {
        if (!isAvailable()) return;
        subscriberShutdown = false;
        subscriberThread = new Thread(() -> runSubscriberLoop(handler), "TryTpa-Redis-Sub");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    private void runSubscriberLoop(BiConsumer<UUID, String> handler) {
        long backoffMs = 1000L;
        while (!subscriberShutdown) {
            Jedis dedicated = null;
            try {
                dedicated = pool.getResource();
                subscriberConnection = dedicated;
                activePubSub = new JedisPubSub() {
                    @Override
                    public void onPMessage(String pattern, String channel, String message) {
                        try {
                            UUID uuid = UUID.fromString(channel.substring(MSG_CHANNEL_PREFIX.length()));
                            handler.accept(uuid, message);
                        } catch (Exception e) {
                            TryTpa.getInstance().getLogger().log(Level.WARNING,
                                    "[Redis] Pub/sub dispatch failed: " + e.getMessage());
                        }
                    }
                };
                dedicated.psubscribe(activePubSub, MSG_CHANNEL_PREFIX + "*");
                // psubscribe returns only when unsubscribed. If shutdown, exit loop.
                if (subscriberShutdown) return;
                backoffMs = 1000L;
            } catch (Exception e) {
                if (subscriberShutdown) return;
                TryTpa.getInstance().getLogger().log(Level.WARNING,
                        "[Redis] Subscriber dropped: " + e.getMessage() + " — reconnecting in " + backoffMs + "ms");
            } finally {
                if (dedicated != null) {
                    try { dedicated.close(); } catch (Exception ignored) {}
                }
            }
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffMs = Math.min(backoffMs * 2L, 30_000L);
        }
    }

    public void stopSubscriber() {
        subscriberShutdown = true;
        JedisPubSub pubSub = activePubSub;
        if (pubSub != null) {
            try { pubSub.punsubscribe(); } catch (Exception ignored) {}
        }
        Jedis conn = subscriberConnection;
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) {}
        }
        Thread t = subscriberThread;
        if (t != null) t.interrupt();
    }

    // ---------------------------------------------------------------------
    // Cleanup
    // ---------------------------------------------------------------------

    public void cleanupPlayer(UUID player) {
        withJedis("cleanupPlayer", jedis -> jedis.del(
                reqKey(RequestType.TPA, player),
                reqKey(RequestType.TPA_HERE, player),
                reqServerKey(RequestType.TPA, player),
                reqServerKey(RequestType.TPA_HERE, player),
                tpaAllKey(player),
                cdKey(RequestType.TPA, player),
                cdKey(RequestType.TPA_HERE, player),
                cdKey(RequestType.TPA_ALL, player)
        ));
    }

    public void close() {
        stopSubscriber();
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Borrows a Jedis from the pool, applies {@code action}, and returns its result.
     * Returns {@code null} and logs on any failure (including pool unavailability).
     */
    private <T> T withJedis(String method, Function<Jedis, T> action) {
        if (!isAvailable()) return null;
        try (Jedis jedis = pool.getResource()) {
            return action.apply(jedis);
        } catch (Exception e) {
            TryTpa.getInstance().getLogger().log(Level.WARNING, "[Redis] Error in " + method + ": " + e.getMessage());
            return null;
        }
    }

    private static String reqKey(RequestType type, UUID uuid)       { return PREFIX + type.getKey() + ":req:" + uuid; }
    private static String reqServerKey(RequestType type, UUID uuid) { return PREFIX + type.getKey() + ":req:" + uuid + ":server"; }
    private static String cdKey(RequestType type, UUID uuid)        { return PREFIX + type.getKey() + ":cd:" + uuid; }
    private static String tpaAllKey(UUID uuid)                      { return PREFIX + "tpaall:req:" + uuid; }
    private static String toggleKey(RequestType type, UUID uuid)    { return TOGGLE_PREFIX + type.getKey() + ":" + uuid; }
    private static String pendingKey(PendingKind kind, UUID uuid) {
        return PENDING_PREFIX + switch (kind) {
            case TPA -> "tpa:";
            case TPA_HERE -> "tpahere:";
            case TPA_ALL -> "tpaall:";
        } + uuid;
    }

    private static String encodeLocationWithServer(Location location, String serverName) {
        return (serverName == null ? "" : serverName) + "|"
                + location.getWorld().getName() + ":"
                + location.getX() + ":" + location.getY() + ":" + location.getZ() + ":"
                + location.getYaw() + ":" + location.getPitch();
    }

    private static Location decodeLocation(String raw) {
        if (raw == null) return null;
        int pipe = raw.indexOf('|');
        String locPart = pipe >= 0 ? raw.substring(pipe + 1) : raw;
        String[] parts = locPart.split(":");
        if (parts.length != 6) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world,
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String decodeServerName(String raw) {
        if (raw == null) return null;
        int pipe = raw.indexOf('|');
        return pipe >= 0 ? raw.substring(0, pipe) : null;
    }

    /** Raw encoded location payload — used when forwarding across servers via pending keys. */
    public String getTpaAllRawPayload(UUID requester) {
        return withJedis("getTpaAllRawPayload", jedis -> jedis.get(tpaAllKey(requester)));
    }

    public static Location decodeLocationPayload(String raw) {
        return decodeLocation(raw);
    }
}

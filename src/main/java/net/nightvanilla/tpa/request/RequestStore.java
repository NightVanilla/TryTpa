package net.nightvanilla.tpa.request;

import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.redis.RedisManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores outstanding TPA-family requests, cooldowns and toggle state.
 * Backed by Redis when available, otherwise by thread-safe in-memory maps.
 */
public final class RequestStore {

    private final RedisManager redis;

    private final Map<RequestType, Map<UUID, UUID>> requests = new EnumMap<>(RequestType.class);
    private final Map<RequestType, Map<UUID, Long>> cooldowns = new EnumMap<>(RequestType.class);
    private final Map<RequestType, Set<UUID>> disabled = new EnumMap<>(RequestType.class);

    private final Map<UUID, Location> tpaAllLocations = new ConcurrentHashMap<>();

    public RequestStore(RedisManager redis) {
        this.redis = redis;
        for (RequestType t : RequestType.values()) {
            requests.put(t, new ConcurrentHashMap<>());
            cooldowns.put(t, new ConcurrentHashMap<>());
            disabled.put(t, ConcurrentHashMap.newKeySet());
        }
    }

    // -- Requests -----------------------------------------------------------

    public void putRequest(RequestType type, UUID requester, UUID target, long ttlSeconds, String serverName) {
        if (redis.isAvailable()) {
            redis.setRequest(type, requester, target, ttlSeconds, serverName);
            return;
        }
        requests.get(type).put(requester, target);
        Bukkit.getGlobalRegionScheduler().runDelayed(
                TryTpa.getInstance(),
                task -> requests.get(type).remove(requester),
                Math.max(1L, 20L * ttlSeconds));
    }

    public UUID getRequest(RequestType type, UUID requester) {
        return redis.isAvailable() ? redis.getRequest(type, requester) : requests.get(type).get(requester);
    }

    public void removeRequest(RequestType type, UUID requester) {
        if (redis.isAvailable()) redis.removeRequest(type, requester);
        else requests.get(type).remove(requester);
    }

    public Set<UUID> getRequestersForTarget(RequestType type, UUID target) {
        if (redis.isAvailable()) return redis.getRequestersForTarget(type, target);
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, UUID> e : requests.get(type).entrySet()) {
            if (e.getValue().equals(target)) result.add(e.getKey());
        }
        return result;
    }

    public String getRequestServerName(RequestType type, UUID requester) {
        return redis.isAvailable() ? redis.getRequestServerName(type, requester) : null;
    }

    // -- TpaAll -------------------------------------------------------------

    public void putTpaAllRequest(UUID requester, Location location, String serverName, long ttlSeconds) {
        if (redis.isAvailable()) {
            redis.setTpaAllRequest(requester, location, serverName, ttlSeconds);
            return;
        }
        tpaAllLocations.put(requester, location);
        Bukkit.getGlobalRegionScheduler().runDelayed(
                TryTpa.getInstance(),
                task -> tpaAllLocations.remove(requester),
                Math.max(1L, 20L * ttlSeconds));
    }

    public Location getTpaAllLocation(UUID requester) {
        return redis.isAvailable() ? redis.getTpaAllLocation(requester) : tpaAllLocations.get(requester);
    }

    public String getTpaAllServerName(UUID requester) {
        return redis.isAvailable() ? redis.getTpaAllServerName(requester) : null;
    }

    public void removeTpaAllRequest(UUID requester) {
        if (redis.isAvailable()) redis.removeTpaAllRequest(requester);
        else tpaAllLocations.remove(requester);
    }

    public Set<UUID> getTpaAllRequesters() {
        return redis.isAvailable() ? redis.getTpaAllRequesters() : new HashSet<>(tpaAllLocations.keySet());
    }

    // -- Cooldowns ----------------------------------------------------------

    public void setCooldown(RequestType type, UUID player, long expiryMillis) {
        if (redis.isAvailable()) redis.setCooldown(type, player, expiryMillis);
        else cooldowns.get(type).put(player, expiryMillis);
    }

    public long getCooldown(RequestType type, UUID player) {
        return redis.isAvailable() ? redis.getCooldown(type, player) : cooldowns.get(type).getOrDefault(player, 0L);
    }

    // -- Toggle -------------------------------------------------------------

    public void setToggle(RequestType type, UUID player, boolean isDisabled) {
        if (redis.isAvailable()) {
            redis.setToggle(type, player, isDisabled);
            return;
        }
        if (isDisabled) disabled.get(type).add(player);
        else disabled.get(type).remove(player);
    }

    public boolean isDisabled(RequestType type, UUID player) {
        return redis.isAvailable() ? redis.isToggled(type, player) : disabled.get(type).contains(player);
    }

    // -- Player resolution --------------------------------------------------

    /**
     * Online player names across the whole network. Safe from the main thread —
     * reads the async-maintained cache when Redis is available, otherwise local.
     */
    public List<String> getOnlinePlayerNames() {
        if (redis.isAvailable()) return new ArrayList<>(redis.getCachedPlayerNames());
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    public String resolvePlayerName(UUID uuid) {
        if (redis.isAvailable()) {
            String name = redis.getPlayerName(uuid);
            if (name != null) return name;
        }
        Player p = Bukkit.getPlayer(uuid);
        return p != null ? p.getName() : null;
    }

    public UUID resolvePlayerUUID(String name) {
        if (redis.isAvailable()) {
            UUID uuid = redis.getPlayerUUID(name);
            if (uuid != null) return uuid;
        }
        Player p = Bukkit.getPlayer(name);
        return p != null ? p.getUniqueId() : null;
    }

    // -- Cleanup ------------------------------------------------------------

    public void removeAllForPlayer(UUID player) {
        if (redis.isAvailable()) {
            redis.cleanupPlayer(player);
            return;
        }
        for (RequestType t : RequestType.values()) {
            requests.get(t).remove(player);
        }
        tpaAllLocations.remove(player);
    }
}

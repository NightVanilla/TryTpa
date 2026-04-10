package net.nightvanilla.tpa;

import net.nightvanilla.tpa.redis.RedisManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RequestStore {

    private final Map<UUID, UUID> tpaRequests = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> tpaHereRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Location> tpaAllRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tpaCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tpaHereCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tpaAllCooldowns = new ConcurrentHashMap<>();

    private final RedisManager redis;

    public RequestStore(RedisManager redis) {
        this.redis = redis;
    }

    // ---- TPA ----

    public void putTpaRequest(UUID requester, UUID target, long expirationSeconds) {
        if (redis.isAvailable()) {
            redis.setRequest("tpa", requester, target, expirationSeconds);
        } else {
            tpaRequests.put(requester, target);
            Bukkit.getScheduler().runTaskLater(TryTpa.getInstance(), () -> tpaRequests.remove(requester), 20 * expirationSeconds);
        }
    }

    public UUID getTpaRequest(UUID requester) {
        return redis.isAvailable() ? redis.getRequest("tpa", requester) : tpaRequests.get(requester);
    }

    public void removeTpaRequest(UUID requester) {
        if (redis.isAvailable()) redis.removeRequest("tpa", requester);
        else tpaRequests.remove(requester);
    }

    public Set<UUID> getTpaRequestersForTarget(UUID target) {
        if (redis.isAvailable()) return redis.getRequestersForTarget("tpa", target);
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, UUID> entry : tpaRequests.entrySet()) {
            if (entry.getValue().equals(target)) result.add(entry.getKey());
        }
        return result;
    }

    public void setTpaCooldown(UUID player, long expiryMillis) {
        if (redis.isAvailable()) redis.setCooldown("tpa", player, expiryMillis);
        else tpaCooldowns.put(player, expiryMillis);
    }

    public long getTpaCooldown(UUID player) {
        return redis.isAvailable() ? redis.getCooldown("tpa", player) : tpaCooldowns.getOrDefault(player, 0L);
    }

    // ---- TpaHere ----

    public void putTpaHereRequest(UUID requester, UUID target, long expirationSeconds) {
        if (redis.isAvailable()) {
            redis.setRequest("tpahere", requester, target, expirationSeconds);
        } else {
            tpaHereRequests.put(requester, target);
            Bukkit.getScheduler().runTaskLater(TryTpa.getInstance(), () -> tpaHereRequests.remove(requester), 20 * expirationSeconds);
        }
    }

    public UUID getTpaHereRequest(UUID requester) {
        return redis.isAvailable() ? redis.getRequest("tpahere", requester) : tpaHereRequests.get(requester);
    }

    public void removeTpaHereRequest(UUID requester) {
        if (redis.isAvailable()) redis.removeRequest("tpahere", requester);
        else tpaHereRequests.remove(requester);
    }

    public Set<UUID> getTpaHereRequestersForTarget(UUID target) {
        if (redis.isAvailable()) return redis.getRequestersForTarget("tpahere", target);
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, UUID> entry : tpaHereRequests.entrySet()) {
            if (entry.getValue().equals(target)) result.add(entry.getKey());
        }
        return result;
    }

    public void setTpaHereCooldown(UUID player, long expiryMillis) {
        if (redis.isAvailable()) redis.setCooldown("tpahere", player, expiryMillis);
        else tpaHereCooldowns.put(player, expiryMillis);
    }

    public long getTpaHereCooldown(UUID player) {
        return redis.isAvailable() ? redis.getCooldown("tpahere", player) : tpaHereCooldowns.getOrDefault(player, 0L);
    }

    // ---- TpaAll ----

    public void putTpaAllRequest(UUID requester, Location location, long expirationSeconds) {
        if (redis.isAvailable()) {
            redis.setTpaAllRequest(requester, location, expirationSeconds);
        } else {
            tpaAllRequests.put(requester, location);
            Bukkit.getScheduler().runTaskLater(TryTpa.getInstance(), () -> tpaAllRequests.remove(requester), 20 * expirationSeconds);
        }
    }

    public Location getTpaAllRequest(UUID requester) {
        return redis.isAvailable() ? redis.getTpaAllRequest(requester) : tpaAllRequests.get(requester);
    }

    public void removeTpaAllRequest(UUID requester) {
        if (redis.isAvailable()) redis.removeTpaAllRequest(requester);
        else tpaAllRequests.remove(requester);
    }

    public void setTpaAllCooldown(UUID player, long expiryMillis) {
        if (redis.isAvailable()) redis.setCooldown("tpaall", player, expiryMillis);
        else tpaAllCooldowns.put(player, expiryMillis);
    }

    public long getTpaAllCooldown(UUID player) {
        return redis.isAvailable() ? redis.getCooldown("tpaall", player) : tpaAllCooldowns.getOrDefault(player, 0L);
    }

    // ---- Cleanup ----

    public void removeAllForPlayer(UUID player) {
        if (redis.isAvailable()) {
            redis.cleanupPlayer(player);
        } else {
            tpaRequests.remove(player);
            tpaHereRequests.remove(player);
            tpaAllRequests.remove(player);
        }
    }

}

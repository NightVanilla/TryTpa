package net.nightvanilla.tpa.request;

import lombok.Getter;

/**
 * Identifies a teleport-request kind and carries the Redis key segment and
 * user-facing message segment used across the plugin.
 */
@Getter
public enum RequestType {

    TPA("tpa", "Tpa"),
    TPA_HERE("tpahere", "TpaHere"),
    TPA_ALL("tpaall", "TpaAll");

    /** Lower-case key used in Redis, commands and config (e.g. {@code tpa}). */
    private final String key;

    /** Capitalized key used in config/message lookups (e.g. {@code Tpa}). */
    private final String configKey;

    RequestType(String key, String configKey) {
        this.key = key;
        this.configKey = configKey;
    }
}

package com.uxplima.uxmessentials.teleport.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.CooldownStartPhase;
import com.uxplima.uxmessentials.teleport.domain.RespawnChain;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchPolicy;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelToggles;

/**
 * A typed read view over the teleport module's {@code teleport.conf} subtree. The use cases consult
 * this rather than dotted config paths, so the config keys live in exactly one place and a reload simply
 * reads a fresh view from the swapped {@link ConfigStore}. Every getter carries a sensible default so a
 * minimal config still yields working behaviour.
 *
 * <p>The store is the module-scoped config (rooted at {@code modules.teleport}); the keys below are
 * relative to that root.
 */
public final class TeleportSettings {

    private final ConfigStore config;

    public TeleportSettings(ConfigStore config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** When a teleport cooldown's clock starts; default {@link CooldownStartPhase#TELEPORT}. */
    public CooldownStartPhase cooldownStartPhase() {
        String raw = config.getString("cooldown-start-phase", "teleport").trim().toUpperCase(java.util.Locale.ROOT);
        return switch (raw) {
            case "REQUEST" -> CooldownStartPhase.REQUEST;
            case "ACCEPT" -> CooldownStartPhase.ACCEPT;
            default -> CooldownStartPhase.TELEPORT;
        };
    }

    /** How long a {@code tpa} request stays pending before it expires; default 60s. */
    public Duration requestTtl() {
        return Duration.ofSeconds(Math.max(1, config.getInt("request-ttl-seconds", 60)));
    }

    /** True when a new request displaces a pending one rather than queueing behind it; default true. */
    public boolean singleRequestDisplace() {
        return config.getBoolean("single-request-displace", true);
    }

    /** The default teleport warmup in seconds when the player holds no tier node; default 3s. */
    public long defaultWarmupSeconds() {
        return Math.max(0, config.getInt("default-warmup", 3));
    }

    /** The default teleport cooldown in seconds when the player holds no tier node; default 5s. */
    public long defaultCooldownSeconds() {
        return Math.max(0, config.getInt("default-cooldown", 5));
    }

    /** The per-axis warmup cancel toggles; move-cancel defaults on, rotation and damage off. */
    public WarmupCancelToggles cancelToggles() {
        return new WarmupCancelToggles(
                config.getBoolean("warmup.cancel-on-move", true),
                config.getBoolean("warmup.cancel-on-rotate", false),
                config.getBoolean("warmup.cancel-on-damage", false));
    }

    /** Whether {@code /back} may return to a death location at all (also gated per-player by permission). */
    public boolean backOnDeathEnabled() {
        return config.getBoolean("back.on-death", true);
    }

    /** The safe-search policy (excluded biomes + claim-awareness) shared across worlds. */
    public SafeSearchPolicy safeSearchPolicy() {
        List<String> excluded = config.getStringList("rtp.excluded-biomes", List.of("ocean", "deep_ocean", "river"));
        return new SafeSearchPolicy(
                excluded.stream().map(BiomeName::of).collect(java.util.stream.Collectors.toSet()),
                config.getBoolean("rtp.claim-aware", true));
    }

    /** The per-world respawn chain; falls back to {@link RespawnChain#vanillaDefault()} when unset. */
    public RespawnChain respawnChain(WorldRef world) {
        Objects.requireNonNull(world, "world");
        List<String> tokens = config.getStringList("respawn.chain." + world.name(), List.of());
        RespawnChain chain = RespawnChain.parse(tokens);
        return chain.isEmpty() ? RespawnChain.vanillaDefault() : chain;
    }

    /** The configured fallback world an {@code /rtp} redirects to when its world has no queue, or empty. */
    public java.util.Optional<String> rtpFallbackWorld(WorldRef world) {
        Objects.requireNonNull(world, "world");
        String name = config.getString("rtp.fallback-world." + world.name(), "");
        return name.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(name);
    }
}

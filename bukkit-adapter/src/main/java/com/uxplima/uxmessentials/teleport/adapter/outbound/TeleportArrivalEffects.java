package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.ArrivalEffect;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Plays the configurable arrival sound and particle for a landed teleport, per {@link TeleportKind}. The
 * effect is read from {@link TeleportSettings#arrivalEffect} (its strings resolved to Bukkit {@link Sound}
 * / {@link Particle} here, the one place that translation lives) and played at the player's location.
 *
 * <p>{@link #arrived} touches the live player, so it hops to the player's entity thread through the kernel
 * {@link Scheduler}; the teleport executor calls it from its arrival callback. An unknown sound/particle
 * name, a disabled verb, or an offline player is a silent no-op — never an exception on the hot path.
 */
@NullMarked
public final class TeleportArrivalEffects {

    private final Server server;
    private final TeleportSettings settings;
    private final Scheduler scheduler;

    public TeleportArrivalEffects(Server server, TeleportSettings settings, Scheduler scheduler) {
        this.server = Objects.requireNonNull(server, "server");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Play the arrival effect for {@code who} after a {@code kind} teleport, if one is configured. */
    public void arrived(PlayerRef who, TeleportKind kind) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        ArrivalEffect effect = settings.arrivalEffect(kind);
        if (!effect.enabled()) {
            return;
        }
        @Nullable Player player = server.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            return;
        }
        scheduler.onEntity(who, () -> play(player, effect));
    }

    private void play(Player player, ArrivalEffect effect) {
        @Nullable Location at = player.getLocation();
        if (at == null) {
            return;
        }
        if (effect.hasSound()) {
            @Nullable Sound sound = resolveSound(effect.sound());
            if (sound != null) {
                player.playSound(at, sound, (float) effect.soundVolume(), (float) effect.soundPitch());
            }
        }
        if (effect.hasParticle()) {
            @Nullable Particle particle = resolveParticle(effect.particle());
            if (particle != null) {
                double spread = effect.particleSpread();
                player.getWorld()
                        .spawnParticle(particle, at, Math.max(0, effect.particleCount()), spread, spread, spread);
            }
        }
    }

    /**
     * Resolve a sound by registry key, accepting both the legacy UPPER_SNAKE form
     * ({@code ENTITY_ENDERMAN_TELEPORT}) and the modern dot-notation form ({@code entity.enderman.teleport}).
     * Returns {@code null} when the name is blank or unrecognised — an unknown name should never throw.
     */
    private static @Nullable Sound resolveSound(String name) {
        @Nullable NamespacedKey key = toKey(name);
        return key == null ? null : Registry.SOUNDS.get(key);
    }

    /**
     * Resolve a particle by registry key using the same normalisation as {@link #resolveSound}.
     * Returns {@code null} when the name is blank or unrecognised.
     */
    private static @Nullable Particle resolveParticle(String name) {
        @Nullable NamespacedKey key = toKey(name);
        return key == null ? null : Registry.PARTICLE_TYPE.get(key);
    }

    /**
     * Build a {@link NamespacedKey} from either a legacy UPPER_SNAKE name ({@code ENTITY_ENDERMAN_TELEPORT})
     * or a modern registry form ({@code entity.enderman.teleport} / {@code minecraft:entity.enderman.teleport}).
     * Returns {@code null} for a blank input.
     */
    private static @Nullable NamespacedKey toKey(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty()) {
            return null;
        }
        // Convert UPPER_SNAKE to dot.notation only when there is no namespace and no dots already
        if (!normalized.contains(":") && !normalized.contains(".")) {
            normalized = normalized.replace('_', '.');
        }
        return NamespacedKey.fromString(normalized);
    }
}

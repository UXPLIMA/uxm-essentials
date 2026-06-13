package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.util.Locale;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;

import org.jspecify.annotations.Nullable;

/**
 * Resolves human-readable config names to Bukkit {@link Sound} and {@link Particle} instances via the
 * server's registry, handling both legacy UPPER_SNAKE enum names and modern dot/underscore key forms.
 *
 * <p>Sound registry keys use dot-separated paths (e.g. {@code entity.enderman.teleport}), so
 * UPPER_SNAKE input is converted by replacing {@code _} with {@code .} when no namespace or dots are
 * already present. Particle registry keys use underscore-separated paths (e.g. {@code end_rod}), so
 * UPPER_SNAKE input is only lowercased — no underscore-to-dot conversion.
 *
 * <p>Both methods are null-on-unknown: a blank or unrecognised name returns {@code null} without
 * throwing, so a config typo on the hot path is always a silent no-op.
 */
public final class BukkitRegistryKeys {

    private BukkitRegistryKeys() {}

    /**
     * Resolve a sound by registry key. Accepts both UPPER_SNAKE ({@code ENTITY_ENDERMAN_TELEPORT}) and
     * dot-notation ({@code entity.enderman.teleport}) or namespaced ({@code minecraft:entity.enderman.teleport})
     * forms. Returns {@code null} when the name is blank or not present in the registry.
     */
    public static @Nullable Sound resolveSound(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty()) {
            return null;
        }
        // Sound keys use dot notation; convert underscore-separated names only when no namespace or dots present
        if (!normalized.contains(":") && !normalized.contains(".")) {
            normalized = normalized.replace('_', '.');
        }
        @Nullable NamespacedKey key = NamespacedKey.fromString(normalized);
        return key == null ? null : Registry.SOUNDS.get(key);
    }

    /**
     * Resolve a particle by registry key. Accepts both UPPER_SNAKE ({@code END_ROD}) and already-lowercase
     * ({@code end_rod}) or namespaced ({@code minecraft:end_rod}) forms. Unlike sounds, particle keys use
     * underscore-separated paths, so underscores are never converted to dots. Returns {@code null} when the
     * name is blank or not present in the registry.
     */
    public static @Nullable Particle resolveParticle(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty()) {
            return null;
        }
        @Nullable NamespacedKey key = NamespacedKey.fromString(normalized);
        return key == null ? null : Registry.PARTICLE_TYPE.get(key);
    }
}

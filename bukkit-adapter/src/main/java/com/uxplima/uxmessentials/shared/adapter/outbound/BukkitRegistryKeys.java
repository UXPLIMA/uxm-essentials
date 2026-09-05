package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;

import org.jspecify.annotations.Nullable;

/**
 * Resolves human-readable config names to Bukkit {@link Sound} and {@link Particle} instances via the
 * server's registry, handling both legacy UPPER_SNAKE enum names and modern dot/underscore key forms.
 *
 * <p>Sound registry keys use dot-separated paths (e.g. {@code entity.enderman.teleport}), and a name written
 * that way is looked up in the registry directly. A name with no dot is read as an UPPER_SNAKE constant and
 * resolved through Bukkit's own constant-to-key mapping rather than by rewriting underscores, because a
 * constant does not say which of its underscores are the key's dots. Particle registry keys use
 * underscore-separated paths (e.g. {@code end_rod}), so UPPER_SNAKE input is only lowercased, with no
 * underscore-to-dot conversion
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
        Objects.requireNonNull(name, "name");
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.contains(":") || normalized.contains(".")) {
            @Nullable NamespacedKey key = NamespacedKey.fromString(normalized);
            return key == null ? null : Registry.SOUNDS.get(key);
        }
        return constantNamed(trimmed);
    }

    /**
     * The sound whose registry key the UPPER_SNAKE {@code name} spells. Every dot in a key path is an underscore
     * in the constant, so the key is found by flattening the registry's own keys the same way and comparing,
     * rather than by rewriting the name: {@code BLOCK_NOTE_BLOCK_PLING} gives no way to know that its second
     * underscore is a dot in {@code block.note_block.pling} and its third is not, so turning every underscore
     * into a dot named a sound that does not exist and the effect went silent with nothing logged.
     *
     * <p>This walks the registry rather than consulting a prepared index. The walk is a few thousand string
     * comparisons and only a constant-form name reaches it, which is small beside the teleport or the menu click
     * that asked for the sound; a cached index would buy little and would have to answer for its own lifetime.
     */
    private static @Nullable Sound constantNamed(String name) {
        String flattened = name.toLowerCase(Locale.ROOT);
        return Registry.SOUNDS
                .keyStream()
                .filter(key -> key.value().replace('.', '_').equals(flattened))
                .findFirst()
                .map(Registry.SOUNDS::get)
                .orElse(null);
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

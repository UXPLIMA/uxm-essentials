package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/vanish/config.conf}: the module enable gate plus the Phase 3 behaviour
 * toggles that govern what a vanished player experiences. Resolved once from the module's scoped {@link ConfigStore}
 * when the module starts and, per the atomic-reload rule, swapped whole on reload, so a command or listener that reads
 * it mid-reload sees one coherent snapshot.
 *
 * <p>Every toggle carries the default the bundled config ships, so an operator who deletes a line falls back to the
 * shipped value rather than to {@code false}. The interaction toggles ({@code silent-chests}, {@code no-hunger},
 * {@code no-damage}, {@code mob-target}, the night-vision and flight buffs) default on because a vanished admin is
 * meant to move through the world unseen and untouched; {@code pickup-items} defaults off so a vanished player does not
 * silently vacuum drops, and each player flips their own preference with {@code /vanish pickup}. The later fake
 * join/quit and action-bar toggles land in Phase 4 with their own keys here.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param silentChests suppress the open animation/sound when a vanished player opens a chest, shulker box, ender
 *     chest, or barrel ({@code silent-chests}, default {@code true})
 * @param pickupItems the default for whether a vanished player picks up items; each player overrides it per-player
 *     with {@code /vanish pickup} ({@code pickup-items}, default {@code false})
 * @param nightVision grant permanent night vision while vanished ({@code night-vision}, default {@code true})
 * @param allowFlight allow flight while vanished, restoring the prior state on reappear ({@code allow-flight},
 *     default {@code true})
 * @param noHunger stop hunger draining while vanished ({@code no-hunger}, default {@code true})
 * @param noDamage make a vanished player take no incoming damage ({@code no-damage}, default {@code true})
 * @param mobTarget stop mobs from targeting, and drop any existing target on, a vanished player ({@code mob-target},
 *     default {@code true})
 */
public record VanishConfig(
        boolean enabled,
        boolean silentChests,
        boolean pickupItems,
        boolean nightVision,
        boolean allowFlight,
        boolean noHunger,
        boolean noDamage,
        boolean mobTarget) {

    /** Resolve the vanish config from the module's scoped {@link ConfigStore} ({@code modules.vanish}). */
    public static VanishConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new VanishConfig(
                config.getBoolean("enabled", true),
                config.getBoolean("silent-chests", true),
                config.getBoolean("pickup-items", false),
                config.getBoolean("night-vision", true),
                config.getBoolean("allow-flight", true),
                config.getBoolean("no-hunger", true),
                config.getBoolean("no-damage", true),
                config.getBoolean("mob-target", true));
    }
}

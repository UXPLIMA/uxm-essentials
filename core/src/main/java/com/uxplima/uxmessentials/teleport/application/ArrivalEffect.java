package com.uxplima.uxmessentials.teleport.application;

import java.util.Objects;

/**
 * A teleport arrival effect described in pure data: a sound and a particle to play when a teleport of a
 * given {@link com.uxplima.uxmessentials.teleport.domain.TeleportKind} lands. The sound and particle are
 * carried as their identifier strings (e.g. {@code "ENTITY_ENDERMAN_TELEPORT"}, {@code "END_ROD"}) so this
 * value stays in {@code :core} with no Bukkit dependency; the adapter resolves the strings to the platform
 * types. An effect is meaningful only when {@link #enabled} is true; {@link #NONE} is the inert default.
 */
public record ArrivalEffect(
        boolean enabled,
        String sound,
        double soundVolume,
        double soundPitch,
        String particle,
        int particleCount,
        double particleSpread) {

    /** The inert effect: nothing plays. */
    public static final ArrivalEffect NONE = new ArrivalEffect(false, "", 1.0, 1.0, "", 0, 0.0);

    public ArrivalEffect {
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(particle, "particle");
    }

    /** Whether a non-blank sound should play. */
    public boolean hasSound() {
        return enabled && !sound.isBlank();
    }

    /** Whether a non-blank particle should spawn. */
    public boolean hasParticle() {
        return enabled && !particle.isBlank();
    }
}

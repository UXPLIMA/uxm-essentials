package com.uxplima.uxmessentials.playerwarps.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;

/**
 * A mutable builder for {@link PlayerWarp}, kept package-private so it is purely an internal mechanism: each
 * {@link PlayerWarp} {@code with*}/transition reads {@link PlayerWarp#toBuilder()}, changes the one field it owns,
 * and calls {@link #build()} — which routes through the canonical {@code PlayerWarp} constructor, so every
 * null-check still fires. Extracting the per-field copy boilerplate here keeps {@code PlayerWarp} small without
 * changing its public surface.
 */
final class PlayerWarpBuilder {

    private PlayerRef owner;
    private PlayerWarpName name;
    private Position location;
    private boolean isPublic;
    private Instant createdAt;
    private long visitors;
    private Optional<String> password;
    private boolean isLocked;
    private java.util.List<WelcomeMessage> welcomeMessages;
    private Optional<String> departureSound;
    private Optional<String> arrivalSound;
    private Optional<String> departureParticle;
    private Optional<String> arrivalParticle;
    private Optional<Double> warmupOverrideSeconds;
    private Optional<Double> cooldownOverrideSeconds;
    private Optional<String> iconMaterial;

    PlayerWarpBuilder(PlayerWarp source) {
        Objects.requireNonNull(source, "source");
        this.owner = source.owner();
        this.name = source.name();
        this.location = source.location();
        this.isPublic = source.isPublic();
        this.createdAt = source.createdAt();
        this.visitors = source.visitors();
        this.password = source.password();
        this.isLocked = source.isLocked();
        this.welcomeMessages = source.welcomeMessages();
        this.departureSound = source.departureSound();
        this.arrivalSound = source.arrivalSound();
        this.departureParticle = source.departureParticle();
        this.arrivalParticle = source.arrivalParticle();
        this.warmupOverrideSeconds = source.warmupOverrideSeconds();
        this.cooldownOverrideSeconds = source.cooldownOverrideSeconds();
        this.iconMaterial = source.iconMaterial();
    }

    PlayerWarpBuilder location(Position value) {
        this.location = value;
        return this;
    }

    PlayerWarpBuilder isPublic(boolean value) {
        this.isPublic = value;
        return this;
    }

    PlayerWarpBuilder visitors(long value) {
        this.visitors = value;
        return this;
    }

    PlayerWarpBuilder password(Optional<String> value) {
        this.password = value;
        return this;
    }

    PlayerWarpBuilder isLocked(boolean value) {
        this.isLocked = value;
        return this;
    }

    PlayerWarpBuilder welcomeMessages(java.util.List<WelcomeMessage> value) {
        this.welcomeMessages = value;
        return this;
    }

    PlayerWarpBuilder departureSound(Optional<String> value) {
        this.departureSound = value;
        return this;
    }

    PlayerWarpBuilder arrivalSound(Optional<String> value) {
        this.arrivalSound = value;
        return this;
    }

    PlayerWarpBuilder departureParticle(Optional<String> value) {
        this.departureParticle = value;
        return this;
    }

    PlayerWarpBuilder arrivalParticle(Optional<String> value) {
        this.arrivalParticle = value;
        return this;
    }

    PlayerWarpBuilder warmupOverrideSeconds(Optional<Double> value) {
        this.warmupOverrideSeconds = value;
        return this;
    }

    PlayerWarpBuilder cooldownOverrideSeconds(Optional<Double> value) {
        this.cooldownOverrideSeconds = value;
        return this;
    }

    PlayerWarpBuilder iconMaterial(Optional<String> value) {
        this.iconMaterial = value;
        return this;
    }

    PlayerWarp build() {
        return new PlayerWarp(
                owner,
                name,
                location,
                isPublic,
                createdAt,
                visitors,
                password,
                isLocked,
                welcomeMessages,
                departureSound,
                arrivalSound,
                departureParticle,
                arrivalParticle,
                warmupOverrideSeconds,
                cooldownOverrideSeconds,
                iconMaterial);
    }
}

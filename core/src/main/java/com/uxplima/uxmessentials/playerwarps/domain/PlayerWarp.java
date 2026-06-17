package com.uxplima.uxmessentials.playerwarps.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;

/**
 * One player-owned warp: the {@link PlayerRef owner} who created it, a {@link PlayerWarpName} unique within
 * that owner's set, the {@link Position} it points at, a public/private visibility flag, and optional gates/custom
 * settings (visitors, password, locked state, welcome message, effects, warmup/cooldown overrides, and a custom icon).
 */
public record PlayerWarp(
        PlayerRef owner,
        PlayerWarpName name,
        Position location,
        boolean isPublic,
        Instant createdAt,
        long visitors,
        Optional<String> password,
        boolean isLocked,
        java.util.List<WelcomeMessage> welcomeMessages,
        Optional<String> departureSound,
        Optional<String> arrivalSound,
        Optional<String> departureParticle,
        Optional<String> arrivalParticle,
        Optional<Double> warmupOverrideSeconds,
        Optional<Double> cooldownOverrideSeconds,
        Optional<String> iconMaterial) {

    public PlayerWarp {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(welcomeMessages, "welcomeMessages");
        Objects.requireNonNull(departureSound, "departureSound");
        Objects.requireNonNull(arrivalSound, "arrivalSound");
        Objects.requireNonNull(departureParticle, "departureParticle");
        Objects.requireNonNull(arrivalParticle, "arrivalParticle");
        Objects.requireNonNull(warmupOverrideSeconds, "warmupOverrideSeconds");
        Objects.requireNonNull(cooldownOverrideSeconds, "cooldownOverrideSeconds");
        Objects.requireNonNull(iconMaterial, "iconMaterial");
    }

    public PlayerWarp(PlayerRef owner, PlayerWarpName name, Position location, boolean isPublic, Instant createdAt) {
        this(
                owner,
                name,
                location,
                isPublic,
                createdAt,
                0L,
                Optional.empty(),
                false,
                java.util.List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public PlayerWarp(
            PlayerRef owner,
            PlayerWarpName name,
            Position location,
            boolean isPublic,
            Instant createdAt,
            long visitors,
            Optional<String> password,
            boolean isLocked) {
        this(
                owner,
                name,
                location,
                isPublic,
                createdAt,
                visitors,
                password,
                isLocked,
                java.util.List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /** A new private warp owned by {@code owner}, created now at {@code location}. */
    public static PlayerWarp create(PlayerRef owner, PlayerWarpName name, Position location, Instant createdAt) {
        return new PlayerWarp(
                owner,
                name,
                location,
                false,
                createdAt,
                0L,
                Optional.empty(),
                false,
                java.util.List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /** A pre-filled builder for the internal transitions; the public surface is unchanged. */
    PlayerWarpBuilder toBuilder() {
        return new PlayerWarpBuilder(this);
    }

    /** A copy re-anchored to {@code newLocation}, keeping the owner, name, visibility, and creation time. */
    public PlayerWarp movedTo(Position newLocation) {
        return toBuilder()
                .location(Objects.requireNonNull(newLocation, "newLocation"))
                .build();
    }

    /** A copy with the visibility set to {@code makePublic}, keeping everything else. */
    public PlayerWarp withVisibility(boolean makePublic) {
        return toBuilder().isPublic(makePublic).build();
    }

    public PlayerWarp incrementedVisitors() {
        return toBuilder().visitors(visitors + 1).build();
    }

    public PlayerWarp withPassword(Optional<String> newPassword) {
        return toBuilder()
                .password(newPassword.map(String::strip).filter(n -> !n.isEmpty()))
                .build();
    }

    public PlayerWarp withLocked(boolean locked) {
        return toBuilder().isLocked(locked).build();
    }

    public PlayerWarp withWelcomeMessages(java.util.List<WelcomeMessage> messages) {
        return toBuilder().welcomeMessages(Objects.requireNonNull(messages)).build();
    }

    public PlayerWarp withDepartureSound(Optional<String> sound) {
        return toBuilder().departureSound(sound).build();
    }

    public PlayerWarp withArrivalSound(Optional<String> sound) {
        return toBuilder().arrivalSound(sound).build();
    }

    public PlayerWarp withDepartureParticle(Optional<String> particle) {
        return toBuilder().departureParticle(particle).build();
    }

    public PlayerWarp withArrivalParticle(Optional<String> particle) {
        return toBuilder().arrivalParticle(particle).build();
    }

    public PlayerWarp withWarmupOverride(Optional<Double> seconds) {
        return toBuilder().warmupOverrideSeconds(seconds).build();
    }

    public PlayerWarp withCooldownOverride(Optional<Double> seconds) {
        return toBuilder().cooldownOverrideSeconds(seconds).build();
    }

    public PlayerWarp withIconMaterial(Optional<String> material) {
        return toBuilder().iconMaterial(material).build();
    }
}

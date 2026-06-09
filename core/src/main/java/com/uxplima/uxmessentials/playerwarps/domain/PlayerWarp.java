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

    /** A copy re-anchored to {@code newLocation}, keeping the owner, name, visibility, and creation time. */
    public PlayerWarp movedTo(Position newLocation) {
        return new PlayerWarp(
                owner,
                name,
                Objects.requireNonNull(newLocation, "newLocation"),
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

    /** A copy with the visibility set to {@code makePublic}, keeping everything else. */
    public PlayerWarp withVisibility(boolean makePublic) {
        return new PlayerWarp(
                owner,
                name,
                location,
                makePublic,
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

    public PlayerWarp incrementedVisitors() {
        return new PlayerWarp(
                owner,
                name,
                location,
                isPublic,
                createdAt,
                visitors + 1,
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

    public PlayerWarp withPassword(Optional<String> newPassword) {
        return new PlayerWarp(
                owner,
                name,
                location,
                isPublic,
                createdAt,
                visitors,
                newPassword.map(String::strip).filter(n -> !n.isEmpty()),
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

    public PlayerWarp withLocked(boolean locked) {
        return new PlayerWarp(
                owner,
                name,
                location,
                isPublic,
                createdAt,
                visitors,
                password,
                locked,
                welcomeMessages,
                departureSound,
                arrivalSound,
                departureParticle,
                arrivalParticle,
                warmupOverrideSeconds,
                cooldownOverrideSeconds,
                iconMaterial);
    }

    public PlayerWarp withWelcomeMessages(java.util.List<WelcomeMessage> messages) {
        return new PlayerWarp(
                owner,
                name,
                location,
                isPublic,
                createdAt,
                visitors,
                password,
                isLocked,
                Objects.requireNonNull(messages),
                departureSound,
                arrivalSound,
                departureParticle,
                arrivalParticle,
                warmupOverrideSeconds,
                cooldownOverrideSeconds,
                iconMaterial);
    }

    public PlayerWarp withDepartureSound(Optional<String> sound) {
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
                sound,
                arrivalSound,
                departureParticle,
                arrivalParticle,
                warmupOverrideSeconds,
                cooldownOverrideSeconds,
                iconMaterial);
    }

    public PlayerWarp withArrivalSound(Optional<String> sound) {
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
                sound,
                departureParticle,
                arrivalParticle,
                warmupOverrideSeconds,
                cooldownOverrideSeconds,
                iconMaterial);
    }

    public PlayerWarp withDepartureParticle(Optional<String> particle) {
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
                particle,
                arrivalParticle,
                warmupOverrideSeconds,
                cooldownOverrideSeconds,
                iconMaterial);
    }

    public PlayerWarp withArrivalParticle(Optional<String> particle) {
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
                particle,
                warmupOverrideSeconds,
                cooldownOverrideSeconds,
                iconMaterial);
    }

    public PlayerWarp withWarmupOverride(Optional<Double> seconds) {
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
                seconds,
                cooldownOverrideSeconds,
                iconMaterial);
    }

    public PlayerWarp withCooldownOverride(Optional<Double> seconds) {
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
                seconds,
                iconMaterial);
    }

    public PlayerWarp withIconMaterial(Optional<String> material) {
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
                material);
    }
}

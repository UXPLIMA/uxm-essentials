package com.uxplima.uxmessentials.warps.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * One server-wide warp: a {@link WarpName}, the {@link Position} it points at, the owner who created it,
 * the moment it was created, and optional gates/custom settings (cost, permission, password, locked state,
 * welcome message, custom sound and particle effects, cooldown/warmup overrides, and a custom icon).
 */
public record Warp(
        WarpName name,
        Position location,
        PlayerRef owner,
        Instant createdAt,
        WarpCost cost,
        Optional<String> requiredPermission,
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

    public Warp {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(requiredPermission, "requiredPermission");
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

    public Warp(
            WarpName name,
            Position location,
            PlayerRef owner,
            Instant createdAt,
            WarpCost cost,
            Optional<String> requiredPermission) {
        this(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    public Warp(
            WarpName name,
            Position location,
            PlayerRef owner,
            Instant createdAt,
            WarpCost cost,
            Optional<String> requiredPermission,
            long visitors,
            Optional<String> password,
            boolean isLocked) {
        this(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    /** A new free, ungated warp created now at {@code location}. */
    public static Warp create(WarpName name, Position location, PlayerRef owner, Instant createdAt) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                WarpCost.free(),
                Optional.empty(),
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

    /**
     * A new warp created now with an optional cost and an optional extra permission gate. A blank
     * permission is treated as "no extra gate".
     */
    public static Warp create(
            WarpName name,
            Position location,
            PlayerRef owner,
            Instant createdAt,
            WarpCost cost,
            Optional<String> requiredPermission) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                normalisePermission(requiredPermission),
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

    /** A copy re-anchored to {@code newLocation}, keeping the name, owner, gates, and original creation time. */
    public Warp movedTo(Position newLocation) {
        return new Warp(
                name,
                Objects.requireNonNull(newLocation, "newLocation"),
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    public Warp withCost(WarpCost newCost) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                Objects.requireNonNull(newCost, "newCost"),
                requiredPermission,
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

    public Warp incrementedVisitors() {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    public Warp withPassword(Optional<String> newPassword) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
                visitors,
                normalisePermission(newPassword),
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

    public Warp withLocked(boolean locked) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    public Warp withWelcomeMessages(java.util.List<WelcomeMessage> messages) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    public Warp withDepartureSound(Optional<String> sound) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    public Warp withArrivalSound(Optional<String> sound) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    public Warp withDepartureParticle(Optional<String> particle) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    public Warp withArrivalParticle(Optional<String> particle) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    public Warp withWarmupOverride(Optional<Double> seconds) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    public Warp withCooldownOverride(Optional<Double> seconds) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    public Warp withIconMaterial(Optional<String> material) {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
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

    /** True when the warp sets a cost the economy gate should charge (a non-free price). */
    public boolean hasCost() {
        return !cost.isFree();
    }

    private static Optional<String> normalisePermission(Optional<String> permission) {
        Objects.requireNonNull(permission, "requiredPermission");
        return permission.map(String::strip).filter(node -> !node.isEmpty());
    }
}

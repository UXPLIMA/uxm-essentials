package com.uxplima.uxmessentials.worlds.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * One step of a {@link VoidRescueChain}: a kind and, for the kinds that need one, its argument. The
 * argument keeps the case an operator typed, because a warp name and a world name are both case-carrying
 * identities; only the leading kind token is matched case-insensitively.
 *
 * @param kind which kind of destination this step names
 * @param argument the warp name for {@link VoidRescueStepKind#WARP}, else {@code null}
 * @param point the parsed coordinates for {@link VoidRescueStepKind#AT}, else {@code null}
 */
public record VoidRescueStep(
        VoidRescueStepKind kind,
        @Nullable String argument,
        @Nullable RescuePoint point) {

    public VoidRescueStep {
        Objects.requireNonNull(kind, "kind");
        if (kind == VoidRescueStepKind.WARP && (argument == null || argument.isBlank())) {
            throw new IllegalArgumentException("a warp rescue step requires a warp name");
        }
        if (kind == VoidRescueStepKind.AT && point == null) {
            throw new IllegalArgumentException("an at rescue step requires a point");
        }
    }

    /** The {@code spawn} step. */
    public static VoidRescueStep spawn() {
        return new VoidRescueStep(VoidRescueStepKind.SPAWN, null, null);
    }

    /** A {@code warp:<name>} step. */
    public static VoidRescueStep warp(String warpName) {
        return new VoidRescueStep(VoidRescueStepKind.WARP, warpName, null);
    }

    /** An {@code at:<world>,<x>,<y>,<z>} step. */
    public static VoidRescueStep at(RescuePoint point) {
        return new VoidRescueStep(VoidRescueStepKind.AT, null, point);
    }

    /**
     * Parse one token. Empty when the token names no known kind or its argument does not parse, which the
     * chain turns into a refusal so an operator sees the typo instead of a step that silently never fires.
     */
    public static Optional<VoidRescueStep> parse(String token) {
        Objects.requireNonNull(token, "token");
        String trimmed = token.strip();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        int colon = trimmed.indexOf(':');
        String head = (colon < 0 ? trimmed : trimmed.substring(0, colon)).toLowerCase(Locale.ROOT);
        String tail = colon < 0 ? "" : trimmed.substring(colon + 1).strip();
        return switch (head) {
            case "spawn" -> colon < 0 ? Optional.of(spawn()) : Optional.empty();
            case "warp" -> tail.isEmpty() ? Optional.empty() : Optional.of(warp(tail));
            case "at" -> RescuePoint.parse(tail).map(VoidRescueStep::at);
            default -> Optional.empty();
        };
    }

    /** The setting text this step was parsed from. */
    public String encode() {
        return switch (kind) {
            case SPAWN -> "spawn";
            case WARP -> "warp:" + Objects.requireNonNull(argument);
            case AT -> "at:" + Objects.requireNonNull(point).encode();
        };
    }

    /** The warp name when this is a {@link VoidRescueStepKind#WARP} step. */
    public Optional<String> warpName() {
        return kind == VoidRescueStepKind.WARP ? Optional.ofNullable(argument) : Optional.empty();
    }

    /** The fixed point when this is an {@link VoidRescueStepKind#AT} step. */
    public Optional<RescuePoint> rescuePoint() {
        return Optional.ofNullable(point);
    }
}

package com.uxplima.uxmessentials.poses.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The poses context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code POSES_SITTING} ↔ {@code poses.sitting}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the context — every
 * message resolves through one of these.
 *
 * <p>Per the i18n contract a disabled module still ships its keys so the catalog stays whole and the locale-parity
 * guard sees the full {@code en} key set. This is the Phase-0 seed: the feedback lines the first behaviour phases
 * ({@code /sit} and the region gate) will resolve through. Later phases add their own keys here as their verbs land.
 */
public enum PosesMessageKey implements MessageKey {

    // Sit/pose feedback — sent to the player when a pose begins and when it ends.
    POSES_SITTING("poses.sitting"),
    POSES_STOOD_UP("poses.stood-up"),

    // Refusal — the pose could not begin here (a protected region, a disabled sub-feature, or nothing to sit on).
    POSES_CANNOT_HERE("poses.cannot-here"),

    // Refusal — sitting is switched off, the player already holds a pose, or the seat is already taken.
    POSES_SIT_DISABLED("poses.sit-disabled"),
    POSES_ALREADY_POSING("poses.already-posing"),
    POSES_SEAT_OCCUPIED("poses.seat-occupied");

    private final String key;

    PosesMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

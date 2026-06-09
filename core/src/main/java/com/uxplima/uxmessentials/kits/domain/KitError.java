package com.uxplima.uxmessentials.kits.domain;

import com.uxplima.uxmessentials.kits.application.KitsMessageKey;

/**
 * The modelled failures a kit operation can produce. Each value carries the {@link KitsMessageKey} the
 * command adapter renders, so a use case returns a {@code Result.err(KitError.X)} and the caller never
 * re-derives the message — the error carries it, and the failure reason and its localized text never drift
 * apart.
 */
public enum KitError {

    /** An id no kit exists under: {@code /kit}, {@code /delkit}, {@code /kiteditor}, {@code /showkit}. */
    NOT_FOUND(KitsMessageKey.KIT_NOT_FOUND),

    /** {@code /createkit} for an id a kit already exists under. */
    ALREADY_EXISTS(KitsMessageKey.KIT_ALREADY_EXISTS),

    /** No kits are defined at all when one was required ({@code /kits} renders its own empty notice). */
    NONE_DEFINED(KitsMessageKey.KIT_NONE),

    /** The player lacks the kit's required permission (the per-kit node). */
    NO_PERMISSION(KitsMessageKey.KIT_NO_PERMISSION),

    /** The kit's cooldown has not yet elapsed for the player. */
    ON_COOLDOWN(KitsMessageKey.KIT_ON_COOLDOWN),

    /** A one-time kit the player has already consumed. */
    ALREADY_CLAIMED(KitsMessageKey.KIT_ALREADY_CLAIMED),

    /** The player does not satisfy one or more of the kit's claim requirements (placeholder conditions). */
    REQUIREMENTS_NOT_MET(KitsMessageKey.KIT_REQUIREMENTS_NOT_MET),

    /** The player cannot pay the kit's cost (only reachable when an economy provider is present). */
    CANNOT_AFFORD(KitsMessageKey.KIT_CANNOT_AFFORD),

    /** The claim was cancelled by an external plugin event. */
    CANCELLED(KitsMessageKey.KIT_CLAIM_CANCELLED);

    private final KitsMessageKey messageKey;

    KitError(KitsMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public KitsMessageKey messageKey() {
        return messageKey;
    }
}

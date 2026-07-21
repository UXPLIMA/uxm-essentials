package com.uxplima.uxmessentials.trade.domain;

import java.util.Objects;

/**
 * One leg of a trade's experience settlement: the {@code payer} side owes {@code points} experience to the other side.
 * An {@code ExperienceTransfer} is derived purely from the two {@link TradeOffer}s a session holds, one leg per side
 * that staked any experience, so the settlement decision stays testable without touching a live player. The recipient
 * is always {@code payer.other()}; naming only the payer keeps the value minimal, mirroring {@link MoneyTransfer}.
 *
 * @param payer the side that staked, and therefore gives away, this experience
 * @param points the strictly-positive whole experience points to move from the payer to the other side
 */
public record ExperienceTransfer(TradeSide payer, long points) {

    public ExperienceTransfer {
        Objects.requireNonNull(payer, "payer");
        if (points <= 0) {
            throw new IllegalArgumentException("experience points must be strictly positive: " + points);
        }
    }

    /** The side that receives this experience, always the opposite of the payer. */
    public TradeSide recipient() {
        return payer.other();
    }
}

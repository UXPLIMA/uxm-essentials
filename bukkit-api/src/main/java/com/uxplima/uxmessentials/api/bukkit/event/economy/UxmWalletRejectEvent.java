package com.uxplima.uxmessentials.api.bukkit.event.economy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmEconomyRejection;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import org.jspecify.annotations.NullMarked;

/** A wallet operation was refused. Nothing moved, and the balance is unchanged. */
@NullMarked
public final class UxmWalletRejectEvent extends UxmEconomyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmMoney requested;
    private final UxmMoney available;
    private final UxmEconomyRejection reason;
    private final Instant occurredAt;

    public UxmWalletRejectEvent(
            UUID ownerId,
            String ownerName,
            UxmMoney requested,
            UxmMoney available,
            UxmEconomyRejection reason,
            Instant occurredAt) {
        super(ownerId, ownerName);
        this.requested = Objects.requireNonNull(requested, "requested");
        this.available = Objects.requireNonNull(available, "available");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** What the operation asked for. */
    public UxmMoney getRequested() {
        return requested;
    }

    /** What the wallet actually holds. */
    public UxmMoney getAvailable() {
        return available;
    }

    /** Why it was refused. */
    public UxmEconomyRejection getReason() {
        return reason;
    }

    /** When it was refused. */
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

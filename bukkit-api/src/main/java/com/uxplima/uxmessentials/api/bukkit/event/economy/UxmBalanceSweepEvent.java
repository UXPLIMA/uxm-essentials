package com.uxplima.uxmessentials.api.bukkit.event.economy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /eco giveall} or {@code /eco resetall} finished changing every account it reaches. Fired once per command,
 * on the global region, when the last part of it is stored, instead of a credit or debit event per account: one
 * command can change hundreds of thousands of balances, many of them of players who are not online.
 *
 * <p>Server-wide, so no player is its subject. A listener that keeps its own copy of balances should read again the
 * ones it holds in {@link #getAmount()}'s currency.
 */
@NullMarked
public final class UxmBalanceSweepEvent extends UxmEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    /** What the command did to each balance. */
    public enum Kind {
        /** {@code /eco giveall}: added the amount. */
        GIVE_ALL,
        /** {@code /eco resetall}: set the balance to the amount, the currency's starting figure. */
        RESET_ALL
    }

    /** Which accounts it reached. */
    public enum Scope {
        /** The accounts of players who have joined. */
        PLAYERS,
        /** Every account, also those other plugins opened through Vault. */
        ALL_ACCOUNTS
    }

    private final Kind kind;
    private final Scope scope;
    private final UxmMoney amount;
    private final int accounts;
    private final int skipped;
    private final UUID actorId;
    private final String actorName;
    private final Instant occurredAt;

    public UxmBalanceSweepEvent(
            Kind kind,
            Scope scope,
            UxmMoney amount,
            int accounts,
            int skipped,
            UUID actorId,
            String actorName,
            Instant occurredAt) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.accounts = accounts;
        this.skipped = skipped;
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.actorName = Objects.requireNonNull(actorName, "actorName");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** Whether the amount was added or set. */
    public Kind getKind() {
        return kind;
    }

    /** Which accounts it reached. */
    public Scope getScope() {
        return scope;
    }

    /** The amount added to each balance, or the balance each was set to. */
    public UxmMoney getAmount() {
        return amount;
    }

    /** How many balances changed. */
    public int getAccounts() {
        return accounts;
    }

    /** How many were left as they were because the amount would have passed the currency's maximum. */
    public int getSkipped() {
        return skipped;
    }

    /** Who ran the command. */
    public UUID getActorId() {
        return actorId;
    }

    /** The name of who ran the command. */
    public String getActorName() {
        return actorName;
    }

    /** When the last part of it was stored. */
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

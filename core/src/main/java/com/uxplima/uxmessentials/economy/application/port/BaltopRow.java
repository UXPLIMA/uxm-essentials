package com.uxplima.uxmessentials.economy.application.port;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * One ranked entry in a {@code /baltop} read-model: an owner and their balance in a single currency. Rows
 * arrive from the provider already sorted descending by balance and bounded by the requested limit, so the
 * adapter renders them in order without re-sorting. Exempt owners ({@code economy.baltop.exempt-permission})
 * are filtered out where the snapshot is built, never here, so a row always represents a leaderboard entry.
 *
 * @param owner the wallet owner this rank belongs to
 * @param balance the owner's {@link Money} in the ranked currency
 */
public record BaltopRow(PlayerRef owner, Money balance) {

    public BaltopRow {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(balance, "balance");
    }
}

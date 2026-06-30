package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.EconomyQuery;

/**
 * The {@code vault} back-end: the server economy reached through the already-resolved {@link EconomyQuery} hook.
 * No provider SDK type appears here — the hook is the seam, and its {@code ABSENT} default already no-ops when no
 * Vault economy is registered. The explicit {@link #available()} guard keeps the no-op contract local to this
 * provider rather than depending on the wrapped query's own no-op behaviour.
 */
final class VaultCurrencyProvider implements CurrencyProvider {

    private final String id;
    private final EconomyQuery economy;

    VaultCurrencyProvider(String id, EconomyQuery economy) {
        this.id = Objects.requireNonNull(id, "id");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean available() {
        return economy.available();
    }

    @Override
    public double balance(UUID player) {
        Objects.requireNonNull(player, "player");
        return available() ? economy.balance(player) : 0;
    }

    @Override
    public boolean has(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        return available() && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        return available() && economy.withdraw(player, amount);
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        return available() && economy.deposit(player, amount);
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}

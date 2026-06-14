package com.uxplima.uxmessentials.vaults.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow economy seam the vaults context owns so a per-action cost can be charged — and a refund paid —
 * <em>without</em> a hard dependency on the economy context. It is the entire economy surface vaults needs,
 * expressed in vaults' own terms: a balance check, a withdrawal (the create/open fee) and a deposit (the
 * refund paid back when a vault is deleted). The economy context supplies an adapter that bridges this to its
 * {@code EconomyProvider}/{@code Wallet}; the vaults context never imports an economy type. This mirrors the
 * homes {@code HomeEconomy} seam.
 *
 * <p>Soft coupling: this port is injected as a {@link java.util.Optional} into {@code VaultCharge}. When no
 * provider is present a configured cost is simply ignored at use time (vault actions are free, as if no cost
 * were configured) and a configured refund is never paid. When a provider is present {@code VaultCharge}
 * withdraws through it before a vault is allocated and deposits through it when a vault is deleted. A
 * {@code withdraw} the player cannot afford fails the action, and the charge happens only once every other
 * guard (the amount quota) has passed.
 */
public interface VaultEconomy {

    /**
     * A no-op economy: affording is always true and a withdraw/deposit does nothing. Wired when economy is
     * absent or disabled so a configured cost degrades to "free" without a special case at every call site.
     */
    VaultEconomy NONE = new VaultEconomy() {
        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return true;
        }

        @Override
        public void withdraw(PlayerRef who, BigDecimal amount) {
            // No economy backing: vault actions are free, so there is nothing to withdraw.
        }

        @Override
        public void deposit(PlayerRef who, BigDecimal amount) {
            // No economy backing: there was nothing charged, so there is nothing to refund.
        }
    };

    /** True when {@code who} can afford {@code amount} in the server's default currency. */
    boolean canAfford(PlayerRef who, BigDecimal amount);

    /** Withdraw {@code amount} from {@code who}'s balance — the create or open fee, charged after the quota gate. */
    void withdraw(PlayerRef who, BigDecimal amount);

    /** Deposit {@code amount} into {@code who}'s balance — the refund paid back when their vault is deleted. */
    void deposit(PlayerRef who, BigDecimal amount);
}

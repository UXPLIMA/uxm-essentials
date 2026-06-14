package com.uxplima.uxmessentials.vaults.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import com.uxplima.uxmessentials.vaults.domain.VaultError;

/**
 * The economy charge gate for vault actions: resolves the cost for an action, skips the charge when it is
 * free, when no economy provider is wired, or when the player holds the bypass node, and otherwise delegates
 * the withdrawal to {@link VaultEconomy}. Mirrors the homes {@code HomeCharge}.
 *
 * <p>This is where the economy <em>soft coupling</em> is expressed. The provider is an {@link Optional}
 * injected at wiring time: when it is absent a configured cost is ignored and the action proceeds for free
 * (and a configured refund is never paid); when it is present the cost is charged through the narrow
 * {@link VaultEconomy} seam after the amount quota has passed, and the refund is deposited on delete. The
 * vaults context therefore never hard-depends on the economy context.
 */
public final class VaultCharge {

    private static final String BYPASS_NODE = "uxmessentials.vault.free";

    private final Permissions permissions;
    private final Optional<VaultEconomy> economy;
    private final VaultChargeSettings settings;

    public VaultCharge(Permissions permissions, Optional<VaultEconomy> economy, VaultChargeSettings settings) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Charge {@code who} the create fee. Returns success immediately when the fee is free, when no economy
     * provider is present, or when the player holds the bypass node; otherwise withdraws the fee and returns
     * {@link VaultError#CANNOT_AFFORD} when the balance was insufficient.
     */
    public Result<Unit, VaultError> chargeCreate(PlayerRef who) {
        return charge(who, settings.costToCreate());
    }

    /**
     * Charge {@code who} the per-open fee. Same gating as {@link #chargeCreate(PlayerRef)}; with a zero
     * {@code cost-to-open} this is always free, so opening an existing vault never costs unless configured.
     */
    public Result<Unit, VaultError> chargeOpen(PlayerRef who) {
        return charge(who, settings.costToOpen());
    }

    /**
     * Refund {@code who} the configured delete refund. A no-op when no economy provider is present, when the
     * refund is zero, or when the player holds the bypass node (they were never charged, so nothing is paid
     * back). The deposit is the inverse of the create fee — it makes deleting a paid-for vault recoverable.
     */
    public void refund(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        BigDecimal refund = settings.refundOnDelete();
        if (economy.isEmpty() || refund.signum() <= 0 || permissions.has(who, BYPASS_NODE)) {
            return;
        }
        economy.get().deposit(who, refund);
    }

    private Result<Unit, VaultError> charge(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        if (amount.signum() <= 0 || economy.isEmpty() || permissions.has(who, BYPASS_NODE)) {
            return Result.ok();
        }
        VaultEconomy provider = economy.get();
        if (!provider.canAfford(who, amount)) {
            return Result.err(VaultError.CANNOT_AFFORD);
        }
        provider.withdraw(who, amount);
        return Result.ok();
    }
}

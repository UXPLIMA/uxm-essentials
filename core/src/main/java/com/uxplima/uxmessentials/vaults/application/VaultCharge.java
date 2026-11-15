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
import com.uxplima.uxmessentials.vaults.domain.VaultPayment;

/**
 * The economy charge gate for vault actions: resolves the cost for an action, skips the charge when it is
 * free, when no economy provider is wired, or when the player holds the bypass node, and otherwise delegates
 * the withdrawal to {@link VaultEconomy}. Mirrors the homes {@code HomeCharge}.
 *
 * <p>This is where the economy <em>soft coupling</em> is expressed. The provider is an {@link Optional}
 * injected at wiring time: when it is absent a configured cost is ignored and the action proceeds for free
 * (and a configured refund is never paid); when it is present the cost is charged through the narrow
 * {@link VaultEconomy} seam after the amount quota has passed, and the refund, capped by what the vault cost,
 * is deposited on delete. The vaults context therefore never hard-depends on the economy context.
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
     * Charge {@code who} the combined create + per-open fee in a single atomic withdrawal, the first-ever
     * allocation of a vault index. Bundling both fees into one debit means a new vault is paid for in one
     * indivisible move: it can never half-charge (take the create fee then fail to take the open fee, leaving
     * the create fee lost with no vault). Succeeds without a charge when the combined fee is free, when no
     * economy provider is present, or when the player holds the bypass node; otherwise withdraws the sum and
     * returns {@link VaultError#CANNOT_AFFORD} when the balance was insufficient.
     *
     * <p>On success the result is the create fee that was taken, in the currency it was taken in, or empty when
     * no create fee was taken. Only the create fee is recorded: the open fee buys this one open, like every later
     * open, so it is spent rather than held by the vault, and leaving it out keeps the refund ceiling at the
     * price of the vault itself.
     */
    public Result<Optional<VaultPayment>, VaultError> chargeAllocate(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        BigDecimal create = settings.costToCreate();
        BigDecimal total = create.add(settings.costToOpen());
        if (total.signum() <= 0 || !charges(who)) {
            return Result.ok(Optional.empty());
        }
        VaultEconomy wallet = economy.orElseThrow();
        if (!wallet.withdraw(who, total)) {
            return Result.err(VaultError.CANNOT_AFFORD);
        }
        return Result.ok(
                create.signum() > 0 ? Optional.of(new VaultPayment(create, wallet.currencyId())) : Optional.empty());
    }

    /**
     * Charge {@code who} the per-open fee: re-opening a vault index that already exists. Same gating as
     * {@link #chargeAllocate(PlayerRef)}; with a zero {@code cost-to-open} this is always free, so re-opening
     * an existing vault never costs unless configured.
     */
    public Result<Unit, VaultError> chargeOpen(PlayerRef who) {
        return charge(who, settings.costToOpen());
    }

    /**
     * Refund {@code who} for deleting a vault they paid {@code paid} for: the configured delete refund, never
     * more than that payment, deposited in the currency it was paid in. A refund above the payment would let a
     * player create and delete vaults in a loop and keep the difference each time, so the payment is the ceiling
     * whatever the config says. A no-op (returning {@code true}) when no economy provider is present, when the
     * refund is zero, or when the vault carries no payment: it was free (a zero cost or the bypass node), or it
     * was created before payments were recorded, and either way paying anything back could create money.
     *
     * <p>Returns {@code false} only when an economy refund was attempted but the provider rejected the credit
     * (e.g. a {@code max-balance} clamp), so the caller can surface the dropped refund. {@code VaultCharge} is
     * pure core and intentionally holds no logger; the boolean is the seam for the adapter to log against.
     */
    public boolean refund(PlayerRef who, Optional<VaultPayment> paid) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(paid, "paid");
        if (economy.isEmpty() || paid.isEmpty()) {
            return true;
        }
        BigDecimal refund = paid.get().refundable(settings.refundOnDelete());
        if (refund.signum() <= 0) {
            return true;
        }
        return economy.get().deposit(who, refund, paid.get().currencyId());
    }

    /** True when a positive fee would actually be withdrawn from {@code who}: an economy is wired and no bypass. */
    private boolean charges(PlayerRef who) {
        return economy.isPresent() && !permissions.has(who, BYPASS_NODE);
    }

    private Result<Unit, VaultError> charge(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        if (amount.signum() <= 0 || !charges(who)) {
            return Result.ok();
        }
        // The withdraw is the guarded debit and reports its own success. There is no preceding canAfford
        // gate, so a concurrent spend (or a min-balance edge) that the guard rejects yields CANNOT_AFFORD
        // rather than a vault allocated without payment.
        return economy.get().withdraw(who, amount) ? Result.ok() : Result.err(VaultError.CANNOT_AFFORD);
    }
}

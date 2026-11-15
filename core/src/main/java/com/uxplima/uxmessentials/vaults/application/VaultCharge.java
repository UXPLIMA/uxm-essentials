package com.uxplima.uxmessentials.vaults.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
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

    private static final String BYPASS_NODE = DestinationPermissions.VAULT_FREE;

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
     * <p>Each fee is denominated in the charging currency before it is taken, so the result reports what really
     * left the balance: the whole withdrawal, which {@link #giveBack} returns if the vault cannot be stored, and
     * the create fee within it, which is what the vault cost. Only the create fee counts as the vault's cost: the
     * open fee buys this one open, like every later open, so it is spent rather than held by the vault, and leaving
     * it out keeps the refund ceiling at the price of the vault itself.
     */
    public Result<Allocation, VaultError> chargeAllocate(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (!charges(who)) {
            return Result.ok(Allocation.FREE);
        }
        VaultEconomy wallet = economy.orElseThrow();
        BigDecimal create = wallet.denominate(settings.costToCreate());
        BigDecimal total = create.add(wallet.denominate(settings.costToOpen()));
        Result<Optional<VaultPayment>, VaultError> taken = take(who, total);
        if (taken.isErr()) {
            return Result.err(taken.errorOrThrow());
        }
        Optional<VaultPayment> cost = paymentOf(create, wallet);
        return Result.ok(new Allocation(taken.orElseThrow(), cost));
    }

    /**
     * Charge {@code who} the per-open fee: re-opening a vault index that already exists. Same gating as
     * {@link #chargeAllocate(PlayerRef)}; with a zero {@code cost-to-open} this is always free, so re-opening
     * an existing vault never costs unless configured. On success the result is what was taken, for
     * {@link #giveBack} when the open cannot go ahead after all.
     */
    public Result<Optional<VaultPayment>, VaultError> chargeOpen(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (!charges(who)) {
            return Result.ok(Optional.empty());
        }
        return take(who, economy.orElseThrow().denominate(settings.costToOpen()));
    }

    /**
     * Return to {@code who} exactly what a charge took ({@code taken}), in the currency it was taken in: the vault
     * it paid for could not be stored or opened, so the player keeps their money. Returns {@code true} when
     * nothing was taken or the credit landed, and {@code false} when the provider rejected it.
     */
    public boolean giveBack(PlayerRef who, Optional<VaultPayment> taken) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(taken, "taken");
        if (economy.isEmpty() || taken.isEmpty()) {
            return true;
        }
        return economy.get().deposit(who, taken.get().amount(), taken.get().currencyId());
    }

    /**
     * Refund {@code who} for deleting a vault they paid {@code paid} for: the configured delete refund, never
     * more than that payment, deposited in the currency it was paid in. A refund above the payment would let a
     * player create and delete vaults in a loop and keep the difference each time, so the payment is the ceiling
     * whatever the config says. A no-op (returning {@code true}) when no economy provider is present, when the
     * refund is zero, or when the vault carries no payment: it was free (a zero cost or the bypass node), or its
     * payment was not recorded, and either way paying anything back could create money.
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

    /** Withdraw an already denominated {@code amount}, reporting it as a payment, or nothing when it is zero. */
    private Result<Optional<VaultPayment>, VaultError> take(PlayerRef who, BigDecimal amount) {
        VaultEconomy wallet = economy.orElseThrow();
        if (amount.signum() <= 0) {
            return Result.ok(Optional.empty());
        }
        // The withdraw is the guarded debit and reports its own success. There is no preceding canAfford
        // gate, so a concurrent spend (or a min-balance edge) that the guard rejects yields CANNOT_AFFORD
        // rather than a vault allocated without payment.
        if (!wallet.withdraw(who, amount)) {
            return Result.err(VaultError.CANNOT_AFFORD);
        }
        return Result.ok(paymentOf(amount, wallet));
    }

    private static Optional<VaultPayment> paymentOf(BigDecimal amount, VaultEconomy wallet) {
        return amount.signum() > 0 ? Optional.of(new VaultPayment(amount, wallet.currencyId())) : Optional.empty();
    }

    /**
     * What a first allocation took.
     *
     * @param taken the whole withdrawal (create and open fee together), or empty when nothing was withdrawn
     * @param vaultCost the create fee within it, what the vault cost and the ceiling on its refund, or empty when
     *     the vault was free
     */
    public record Allocation(Optional<VaultPayment> taken, Optional<VaultPayment> vaultCost) {

        /** Nothing was withdrawn and the vault cost nothing. */
        public static final Allocation FREE = new Allocation(Optional.empty(), Optional.empty());

        public Allocation {
            Objects.requireNonNull(taken, "taken");
            Objects.requireNonNull(vaultCost, "vaultCost");
        }
    }
}

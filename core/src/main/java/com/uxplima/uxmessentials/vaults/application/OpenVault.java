package com.uxplima.uxmessentials.vaults.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultAmount;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultPayment;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;

/**
 * {@code /vault <n>}: resolve and hand back the vault a player wants to open. The amount quota
 * ({@code uxmessentials.vault.amount.<n>}) gates which indices the owner may reach; the size quota
 * ({@code uxmessentials.vault.size.<rows>}) sets how tall a freshly allocated or re-opened vault is. An
 * already-owned vault re-opens regardless of a since-shrunk amount quota. A player never loses access to
 * items they already stored, and adopts the owner's current size on open. A not-yet-allocated vault within
 * the amount quota is created empty.
 *
 * <p>The economy charge is gated through {@link VaultCharge} in the same quota→charge→allocate order
 * {@code CreateHomeAtSlot} uses (the free quota check runs before the paid gate). A first allocation pays the
 * combined create + per-open fee in a single atomic withdrawal, so a new vault is never half-charged;
 * re-opening an existing vault pays only the per-open fee. Each fee is free unless its config cost is
 * positive, so with economy disabled or unwired every open proceeds for free. A fee the player cannot afford
 * fails the open with {@link VaultError#CANNOT_AFFORD} and writes nothing.
 *
 * <p>The open fee is taken before the window exists, because the withdrawal reaches the economy and may not run on
 * the tick, so it comes back as a {@link VaultOpenFee} the adapter spends once the window is showing or gives back
 * when the window never shows. Taking it only once the window showed would mean a window already in front of a player
 * who then turns out unable to pay.
 *
 * <p>This use case is pure: it loads or allocates the {@link Vault} and returns it (or a {@link VaultError}),
 * leaving the GUI open and the {@code VaultOpened} event to the adapter, which knows the live player. The
 * allocation is persisted up front so the vault exists as a row the close-save can update.
 */
public final class OpenVault {

    private final VaultRepository repository;
    private final VaultAmountQuota amountQuota;
    private final VaultSizeQuota sizeQuota;
    private final VaultCharge charge;
    private final Clock clock;

    public OpenVault(
            VaultRepository repository,
            VaultAmountQuota amountQuota,
            VaultSizeQuota sizeQuota,
            VaultCharge charge,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.amountQuota = Objects.requireNonNull(amountQuota, "amountQuota");
        this.sizeQuota = Objects.requireNonNull(sizeQuota, "sizeQuota");
        this.charge = Objects.requireNonNull(charge, "charge");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Resolve {@code owner}'s vault at one-based {@code index}, allocating it within quota when absent. The open fee is
     * taken here, and the result carries it for the caller to spend once the window shows or to give back when it
     * never does.
     */
    public Result<OpenedVault, VaultError> open(PlayerRef owner, int index) {
        Objects.requireNonNull(owner, "owner");
        VaultId id = VaultId.of(owner, index);
        VaultSize size = sizeQuota.resolve(owner);
        return repository
                .find(id)
                .map(existing -> reopen(owner, existing, size))
                .orElseGet(() -> allocate(owner, id, index, size));
    }

    private Result<OpenedVault, VaultError> reopen(PlayerRef owner, Vault existing, VaultSize size) {
        Result<Optional<VaultPayment>, VaultError> charged = charge.chargeOpen(owner);
        if (charged.isErr()) {
            return Result.err(charged.errorOrThrow());
        }
        Vault opened = existing.openedAt(size);
        // The size quota changed since this vault was last saved; persist the new height so the row matches the
        // GUI the player now sees. An update rather than an upsert: a vault deleted since it was read must not
        // come back, so the open fails and the open fee goes back.
        if (opened != existing && !repository.update(opened, VaultRepository.Columns.SIZE)) {
            charge.giveBack(owner, charged.orElseThrow());
            return Result.err(VaultError.VAULT_UNKNOWN);
        }
        return Result.ok(new OpenedVault(opened, charge.openFee(owner, charged.orElseThrow())));
    }

    private Result<OpenedVault, VaultError> allocate(PlayerRef owner, VaultId id, int index, VaultSize size) {
        VaultAmount amount = amountQuota.resolve(owner);
        if (!amount.allows(index)) {
            return Result.err(VaultError.AMOUNT_EXCEEDED);
        }
        // Quota passed (the free check); charge the combined create + per-open fee in one atomic withdrawal
        // before allocating, so a player who cannot afford the vault never leaves a phantom row behind and the
        // create fee is never taken on its own when the open fee cannot be paid.
        Result<VaultCharge.Allocation, VaultError> charged = charge.chargeAllocate(owner);
        if (charged.isErr()) {
            return Result.err(charged.errorOrThrow());
        }
        VaultCharge.Allocation paid = charged.orElseThrow();
        // The create fee is stored with the row because it is the ceiling on what deleting this vault refunds.
        Vault allocated =
                Vault.allocate(id, size, clock.instant(), paid.vaultCost().orElse(null));
        boolean created;
        try {
            created = repository.create(allocated);
        } catch (RuntimeException failure) {
            return afterFailedCreate(owner, allocated, paid, size, failure);
        }
        if (created) {
            return Result.ok(new OpenedVault(allocated, openFee(owner, paid)));
        }
        // Another open of the same index stored its vault first. That vault was paid for by that open, so this
        // one gives back everything it took and shows the stored vault without charging again.
        charge.giveBack(owner, paid.taken());
        return repository
                .find(id)
                .map(stored ->
                        Result.<OpenedVault, VaultError>ok(new OpenedVault(stored.openedAt(size), VaultOpenFee.free())))
                .orElseGet(() -> Result.err(VaultError.VAULT_UNKNOWN));
    }

    /**
     * A create that threw may still have committed. Give the money back only when no row exists at the vault's id:
     * refunding a vault that was stored with its payment would let its delete refund it a second time. A row that is
     * there is opened instead. It cannot be told apart from this open's own row by its payment (the column rounds an
     * amount finer than four places), and both are the same owner's vault at the same index, so treating it as paid
     * for by this open can at worst cost this owner the second fee, never create money. When even the check fails,
     * nothing is refunded for the same reason, and the original failure goes up.
     */
    private Result<OpenedVault, VaultError> afterFailedCreate(
            PlayerRef owner, Vault allocated, VaultCharge.Allocation paid, VaultSize size, RuntimeException failure) {
        Optional<Vault> stored;
        try {
            stored = repository.find(allocated.id());
        } catch (RuntimeException unreadable) {
            failure.addSuppressed(unreadable);
            throw failure;
        }
        if (stored.isPresent()) {
            return Result.ok(new OpenedVault(stored.get().openedAt(size), openFee(owner, paid)));
        }
        // The money left but no vault was stored: give it back before the failure goes up.
        charge.giveBack(owner, paid.taken());
        throw failure;
    }

    /**
     * The open fee within a first allocation's withdrawal. Only that part goes back when the window never shows: the
     * vault was stored, so the create fee bought it whether or not it was looked at.
     */
    private VaultOpenFee openFee(PlayerRef owner, VaultCharge.Allocation paid) {
        return charge.openFee(owner, paid.openFee());
    }
}

package com.uxplima.uxmessentials.vaults.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultPayment;

/**
 * Outbound port for durable, per-owner vault storage. A vault's queryable facts (owner, index, size,
 * last-touched) are first-class columns and only its {@code ItemStack[]} contents serialize, per the
 * architecture persistence invariant, but that split is the adapter's concern; the application sees the whole
 * {@link Vault} aggregate. Vaults are DB-backed and survive a world rollback (the same hard invariant the
 * economy ledger holds), never PDC. The jOOQ adapter implements this; the use cases depend only on the contract.
 */
public interface VaultRepository {

    /** The owner's vault at this id, or empty when they have not opened one there yet. */
    Optional<Vault> find(VaultId id);

    /** The indices of the vaults the owner currently has rows for, ascending: backs the {@code /vault} listing. */
    List<Integer> ownedIndices(PlayerRef owner);

    /**
     * The presentation summary (index, display name, icon material name) of every vault the owner has rows for,
     * ascending by index. One read that backs the {@code /vault} listing and the selector GUI without loading
     * each full {@link Vault} aggregate. Empty when the owner has no vaults yet.
     */
    List<VaultSummary> summaries(PlayerRef owner);

    /** How many vaults the owner currently has rows for: the count the amount quota is checked against. */
    int count(PlayerRef owner);

    /**
     * Persist {@code vault}, upserting on the {@code (owner, index)} key: a re-save of an existing vault
     * overwrites its size, contents, name, icon and last-touched in place, and a vault with no row is inserted.
     * Only a staff window and a window still open when the module stops save this way, because for them losing
     * the items would be worse than keeping the vault. Every other write to an existing vault goes through
     * {@link #update}, so it cannot bring back a vault that was deleted. The upsert never writes a payment, and
     * never changes one already stored, so a vault it brings back carries none and cannot be refunded.
     */
    void save(Vault vault);

    /**
     * Overwrite the size, contents, name, icon and last-touched of the existing row at the vault's id, leaving
     * its payment as it is, and report whether that row was there. A vault deleted in the meantime stays deleted:
     * nothing is inserted and the result is {@code false}.
     */
    boolean update(Vault vault);

    /**
     * Insert a freshly allocated {@code vault} together with what its owner paid for it ({@link Vault#paid()}),
     * and report whether it was inserted. This is the only write that stores a payment. When a row already exists
     * at the vault's id nothing is written and the result is {@code false}, keeping that row's contents and
     * payment, so the caller can give back what it charged for the vault it could not store.
     */
    boolean create(Vault vault);

    /**
     * Remove the vault row at {@code id} only if it still records {@code expected} as its payment (empty matching
     * a row that records none), and report whether a row was removed. The payment check and the removal are one
     * statement, so the payment a caller refunds is the payment of the row it removed: a row deleted and brought
     * back in between, or two deletes racing for the same vault, can never pay out the same payment twice.
     * Deleting an id with no matching row is a no-op that returns {@code false}, never an error. The cache
     * invalidation and cross-server {@code VaultChanged} emit are the adapter's concern; the use case depends only
     * on this contract.
     */
    boolean delete(VaultId id, Optional<VaultPayment> expected);

    /**
     * Delete every vault row whose last-touched instant is strictly before {@code cutoff}, returning the number
     * of rows removed (the inactive-vault cleanup sweep). A vault not touched since {@code cutoff} has neither
     * been opened nor saved in that window, so it is purged to reclaim storage. The cache invalidation is the
     * adapter's concern; the use case depends only on this contract.
     */
    int deleteUntouchedBefore(Instant cutoff);
}

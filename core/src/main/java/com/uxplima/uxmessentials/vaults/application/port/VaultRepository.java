package com.uxplima.uxmessentials.vaults.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;

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
     * overwrites its size, contents and last-touched in place rather than inserting a second row. This is the
     * write-through every {@code InventoryClose} save and every fresh allocation goes through.
     */
    void save(Vault vault);

    /**
     * Insert a freshly allocated {@code vault} together with what its owner paid for it
     * ({@link Vault#paid()}). This is the only write that stores a payment: {@link #save} never writes one, and
     * never changes one already stored. A close-save that re-inserts a vault deleted while its window was open
     * therefore carries no payment, so the same payment can never be refunded twice. When a row already exists
     * at the vault's id this is a no-op, keeping that row's contents and payment.
     */
    void create(Vault vault);

    /**
     * Remove the vault row at {@code id}, freeing the owner's quota slot, and report whether a row was removed.
     * Idempotent: deleting an id with no row is a no-op that returns {@code false}, never an error. Only the
     * call that removed the row sees {@code true}, so two deletes racing for the same vault refund it once. The
     * cache invalidation and cross-server {@code VaultChanged} emit are the adapter's concern; the use case
     * depends only on this contract.
     */
    boolean delete(VaultId id);

    /**
     * Delete every vault row whose last-touched instant is strictly before {@code cutoff}, returning the number
     * of rows removed (the inactive-vault cleanup sweep). A vault not touched since {@code cutoff} has neither
     * been opened nor saved in that window, so it is purged to reclaim storage. The cache invalidation is the
     * adapter's concern; the use case depends only on this contract.
     */
    int deleteUntouchedBefore(Instant cutoff);
}

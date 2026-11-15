package com.uxplima.uxmessentials.vaults.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultPayment;
import org.jspecify.annotations.Nullable;

/**
 * Forwards to {@link InMemoryVaults}, but runs what another server or a second command does at the worst moment:
 * between a use case's read and its next write. A step armed for the next delete, create or update runs once, right
 * before that call reaches the rows, and a create can be made to fail as a broken database would.
 */
final class InterleavedVaults implements VaultRepository {

    private final InMemoryVaults rows;
    private @Nullable Runnable beforeDelete;
    private @Nullable Runnable beforeCreate;
    private @Nullable Runnable beforeUpdate;
    private boolean createFails;

    InterleavedVaults(InMemoryVaults rows) {
        this.rows = rows;
    }

    /** Run {@code step} once, right before the next delete reaches the rows. */
    void beforeNextDelete(Runnable step) {
        beforeDelete = step;
    }

    /** Run {@code step} once, right before the next create reaches the rows. */
    void beforeNextCreate(Runnable step) {
        beforeCreate = step;
    }

    /** Run {@code step} once, right before the next update reaches the rows. */
    void beforeNextUpdate(Runnable step) {
        beforeUpdate = step;
    }

    /** Make the next create throw, as a lost database connection does. */
    void failNextCreate() {
        createFails = true;
    }

    @Override
    public Optional<Vault> find(VaultId id) {
        return rows.find(id);
    }

    @Override
    public List<Integer> ownedIndices(PlayerRef owner) {
        return rows.ownedIndices(owner);
    }

    @Override
    public List<VaultSummary> summaries(PlayerRef owner) {
        return rows.summaries(owner);
    }

    @Override
    public int count(PlayerRef owner) {
        return rows.count(owner);
    }

    @Override
    public void save(Vault vault) {
        rows.save(vault);
    }

    @Override
    public boolean update(Vault vault) {
        Runnable step = beforeUpdate;
        beforeUpdate = null;
        if (step != null) {
            step.run();
        }
        return rows.update(vault);
    }

    @Override
    public boolean create(Vault vault) {
        Runnable step = beforeCreate;
        beforeCreate = null;
        if (step != null) {
            step.run();
        }
        if (createFails) {
            createFails = false;
            throw new IllegalStateException("the database went away");
        }
        return rows.create(vault);
    }

    @Override
    public boolean delete(VaultId id, Optional<VaultPayment> expected) {
        Runnable step = beforeDelete;
        beforeDelete = null;
        if (step != null) {
            step.run();
        }
        return rows.delete(id, expected);
    }

    @Override
    public int deleteUntouchedBefore(Instant cutoff) {
        return rows.deleteUntouchedBefore(cutoff);
    }
}

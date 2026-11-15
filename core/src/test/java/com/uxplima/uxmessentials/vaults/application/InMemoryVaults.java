package com.uxplima.uxmessentials.vaults.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultPayment;
import com.uxplima.uxmessentials.vaults.domain.VaultStamp;

/** Vault rows keyed by owner and index, upserted on save, with a count of every write for no-churn checks. */
final class InMemoryVaults implements VaultRepository {

    private final Map<VaultId, Vault> rows = new LinkedHashMap<>();
    private int saves;

    void put(Vault vault) {
        rows.put(vault.id(), vault);
    }

    int saves() {
        return saves;
    }

    int size() {
        return rows.size();
    }

    @Override
    public Optional<Vault> find(VaultId id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public List<Integer> ownedIndices(PlayerRef owner) {
        return rows.keySet().stream()
                .filter(id -> id.owner().equals(owner.uuid()))
                .map(VaultId::index)
                .sorted()
                .toList();
    }

    @Override
    public List<VaultSummary> summaries(PlayerRef owner) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int count(PlayerRef owner) {
        return ownedIndices(owner).size();
    }

    /**
     * Like the real store: a save neither writes nor changes a payment (only {@link #create} records one), and an
     * existing row takes the contents columns only.
     */
    @Override
    public VaultStamp save(Vault vault) {
        saves++;
        Vault stored = rows.get(vault.id());
        if (stored == null) {
            // An inserted row is always a new lineage, as the real store does it.
            VaultStamp stamp = new VaultStamp(UUID.randomUUID().toString(), 1L);
            rows.put(
                    vault.id(),
                    Vault.of(
                            vault.id(),
                            vault.size(),
                            vault.contents(),
                            vault.displayName(),
                            vault.iconMaterial(),
                            vault.lastTouched(),
                            null,
                            stamp));
            return stamp;
        }
        return writeOver(stored, vault);
    }

    @Override
    public Optional<VaultStamp> writeContents(Vault vault) {
        Vault stored = rows.get(vault.id());
        if (stored == null) {
            return Optional.empty();
        }
        saves++;
        return Optional.of(writeOver(stored, vault));
    }

    /** The row takes the written items and its version goes up by one, as the database does it. */
    private VaultStamp writeOver(Vault stored, Vault written) {
        VaultStamp stamp =
                new VaultStamp(stored.stamp().lineage(), stored.stamp().version() + 1);
        rows.put(written.id(), stored.withContentsOf(written, stamp));
        return stamp;
    }

    @Override
    public boolean update(Vault vault, Columns columns) {
        Vault stored = rows.get(vault.id());
        if (stored == null) {
            return false;
        }
        saves++;
        Vault updated =
                switch (columns) {
                    case PRESENTATION -> stored.renamedTo(vault.displayName()).iconSet(vault.iconMaterial());
                    case SIZE -> stored.openedAt(vault.size());
                };
        rows.put(vault.id(), updated);
        return true;
    }

    @Override
    public boolean create(Vault vault) {
        saves++;
        return rows.putIfAbsent(vault.id(), vault) == null;
    }

    @Override
    public boolean delete(VaultId id, Optional<VaultPayment> expected) {
        Vault stored = rows.get(id);
        if (stored == null || !stored.paid().equals(expected)) {
            return false;
        }
        rows.remove(id);
        return true;
    }

    @Override
    public int deleteUntouchedBefore(Instant cutoff) {
        List<VaultId> stale = new ArrayList<>();
        rows.forEach((id, vault) -> {
            if (vault.lastTouched().isBefore(cutoff)) {
                stale.add(id);
            }
        });
        stale.forEach(rows::remove);
        return stale.size();
    }
}

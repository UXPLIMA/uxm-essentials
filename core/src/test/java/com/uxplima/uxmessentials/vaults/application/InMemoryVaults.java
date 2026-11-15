package com.uxplima.uxmessentials.vaults.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;

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

    /** Like the real store, a save neither writes nor changes a payment: only {@link #create} records one. */
    @Override
    public void save(Vault vault) {
        saves++;
        Vault stored = rows.get(vault.id());
        rows.put(
                vault.id(),
                Vault.of(
                        vault.id(),
                        vault.size(),
                        vault.contents(),
                        vault.displayName(),
                        vault.iconMaterial(),
                        vault.lastTouched(),
                        stored == null ? null : stored.paid().orElse(null)));
    }

    @Override
    public void create(Vault vault) {
        saves++;
        rows.putIfAbsent(vault.id(), vault);
    }

    @Override
    public boolean delete(VaultId id) {
        return rows.remove(id) != null;
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

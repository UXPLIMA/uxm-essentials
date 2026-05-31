package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import org.jspecify.annotations.NullMarked;

/**
 * {@link VaultsPlaceholders} over the vaults context's {@link VaultRepository}. The count is read through
 * the same cached repository the {@code /vault} listing uses, so the placeholder matches the listing.
 */
@NullMarked
public final class RepositoryVaultsPlaceholders implements VaultsPlaceholders {

    private final VaultRepository repository;

    public RepositoryVaultsPlaceholders(VaultRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public int count(PlayerRef who) {
        return repository.count(Objects.requireNonNull(who, "who"));
    }
}

package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultSizeQuota;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.VaultAmount;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.jspecify.annotations.NullMarked;

/**
 * {@link VaultsPlaceholders} over the vaults context's {@link VaultRepository} and the two quota reducers the
 * {@code /vault} use cases hold. The count is read through the same cached repository the {@code /vault}
 * listing uses, and the max/size quotas resolve through the same {@link VaultAmountQuota} and
 * {@link VaultSizeQuota} {@code OpenVault} enforces, so the placeholders match what the command admits.
 *
 * <p>An unlimited vault-count quota maps to a negative {@link #max(PlayerRef)} the resolver renders as the
 * infinity marker, mirroring the homes-limit seam.
 */
@NullMarked
public final class RepositoryVaultsPlaceholders implements VaultsPlaceholders {

    private final VaultRepository repository;
    private final VaultAmountQuota amountQuota;
    private final VaultSizeQuota sizeQuota;

    public RepositoryVaultsPlaceholders(
            VaultRepository repository, VaultAmountQuota amountQuota, VaultSizeQuota sizeQuota) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.amountQuota = Objects.requireNonNull(amountQuota, "amountQuota");
        this.sizeQuota = Objects.requireNonNull(sizeQuota, "sizeQuota");
    }

    @Override
    public int count(PlayerRef who) {
        return repository.count(Objects.requireNonNull(who, "who"));
    }

    @Override
    public int max(PlayerRef who) {
        VaultAmount amount = amountQuota.resolve(Objects.requireNonNull(who, "who"));
        return amount.unlimited() ? -1 : amount.cap();
    }

    @Override
    public int size(PlayerRef who) {
        VaultSize size = sizeQuota.resolve(Objects.requireNonNull(who, "who"));
        return size.rows();
    }
}

package com.uxplima.uxmessentials.vaults.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultAmount;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;

/**
 * {@code /vault <n>}: resolve and hand back the vault a player wants to open. The amount quota
 * ({@code uxmessentials.vault.amount.<n>}) gates which indices the owner may reach; the size quota
 * ({@code uxmessentials.vault.size.<rows>}) sets how tall a freshly allocated or re-opened vault is. An
 * already-owned vault re-opens regardless of a since-shrunk amount quota — a player never loses access to
 * items they already stored — and adopts the owner's current size on open. A not-yet-allocated vault within
 * the amount quota is created empty.
 *
 * <p>This use case is pure: it loads or allocates the {@link Vault} and returns it (or a {@link VaultError}),
 * leaving the GUI open and the {@code VaultOpened} event to the adapter, which knows the live player. The
 * allocation is persisted up front so the vault exists as a row the close-save can upsert against.
 */
public final class OpenVault {

    private final VaultRepository repository;
    private final VaultAmountQuota amountQuota;
    private final VaultSizeQuota sizeQuota;
    private final Clock clock;

    public OpenVault(VaultRepository repository, VaultAmountQuota amountQuota, VaultSizeQuota sizeQuota, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.amountQuota = Objects.requireNonNull(amountQuota, "amountQuota");
        this.sizeQuota = Objects.requireNonNull(sizeQuota, "sizeQuota");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Resolve {@code owner}'s vault at one-based {@code index}, allocating it within quota when absent. */
    public Result<Vault, VaultError> open(PlayerRef owner, int index) {
        Objects.requireNonNull(owner, "owner");
        VaultId id = VaultId.of(owner, index);
        VaultSize size = sizeQuota.resolve(owner);
        return repository
                .find(id)
                .map(existing -> Result.<Vault, VaultError>ok(reopen(existing, size)))
                .orElseGet(() -> allocate(owner, id, index, size));
    }

    private Vault reopen(Vault existing, VaultSize size) {
        Vault opened = existing.openedAt(size);
        if (opened != existing) {
            // The size quota changed since this vault was last saved; persist the new height so the row matches
            // the GUI the player now sees and the close-save upserts against the current size.
            repository.save(opened);
        }
        return opened;
    }

    private Result<Vault, VaultError> allocate(PlayerRef owner, VaultId id, int index, VaultSize size) {
        VaultAmount amount = amountQuota.resolve(owner);
        if (!amount.allows(index)) {
            return Result.err(VaultError.AMOUNT_EXCEEDED);
        }
        Vault allocated = Vault.allocate(id, size, clock.instant());
        repository.save(allocated);
        return Result.ok(allocated);
    }
}

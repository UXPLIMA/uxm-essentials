package com.uxplima.uxmessentials.vaults.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;

/**
 * The write-through that runs when an open vault GUI closes ({@code InventoryCloseEvent}). The adapter resolves
 * the owning {@link Vault} from the inventory holder, serialises the live inventory into opaque
 * {@link VaultContents}, and hands both here; this use case applies the {@link Vault#withContents} transition,
 * persists the result through the repository, and publishes the {@code VaultContentsChanged} event. A vault is
 * the authority for its items, not the GUI, so the close is what makes the stored state durable, and a
 * region-safe save is the adapter's job to schedule before calling in.
 */
public final class SaveVault {

    private final VaultRepository repository;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SaveVault(VaultRepository repository, DomainEventPublisher events, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Write {@code newContents} into {@code vault} as {@code owner}'s save and publish the change, returning
     * whether the vault was still there. A vault deleted while its window was open is not brought back: nothing
     * is written and the result is {@code false}, so the caller hands the window's items to the player instead.
     * Bringing it back would give the player a vault they had already been refunded for, or undo a staff delete.
     */
    public boolean save(PlayerRef owner, Vault vault, VaultContents newContents) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(newContents, "newContents");
        Vault.Change change = vault.withContents(owner, newContents, clock.instant());
        if (!repository.update(change.result())) {
            return false;
        }
        events.publish(change.event());
        return true;
    }

    /**
     * Write {@code newContents} into {@code vault} whether or not its row still exists, for a staff window: the
     * staff member is not the owner the items belong to, and it is how staff fill a vault the owner has not opened
     * yet. A vault this brings back records no payment, so deleting it again refunds nothing.
     */
    public void keep(PlayerRef owner, Vault vault, VaultContents newContents) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(newContents, "newContents");
        Vault.Change change = vault.withContents(owner, newContents, clock.instant());
        repository.save(change.result());
        events.publish(change.event());
    }
}

package com.uxplima.uxmessentials.vaults.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultStamp;
import org.jspecify.annotations.Nullable;

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

    public SaveVault(VaultRepository repository, DomainEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * Write {@code newContents} into {@code vault} as {@code owner}'s save, with {@code at} as the vault's last-touched
     * instant and {@code writer} recorded as the row's writer, and publish the change. Returns the row's stamp after
     * the write, or empty when nothing was written. A vault deleted while its window was open is not brought back:
     * that would give the player a vault they had already been refunded for, or undo a staff delete. With
     * {@code onlyAt} the write applies only while the row is still at that stamp or already carries {@code writer}
     * (see {@link VaultRepository#writeContents(Vault, String, VaultStamp)}), so it never writes over what another
     * server wrote.
     */
    public Optional<VaultStamp> save(
            PlayerRef owner,
            Vault vault,
            VaultContents newContents,
            Instant at,
            String writer,
            @Nullable VaultStamp onlyAt) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(newContents, "newContents");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(writer, "writer");
        Vault.Change change = vault.withContents(owner, newContents, at);
        Optional<VaultStamp> written = repository.writeContents(change.result(), writer, onlyAt);
        if (written.isPresent()) {
            events.publish(change.event());
        }
        return written;
    }

    /**
     * Write {@code newContents} into {@code vault} whether or not its row still exists, for a staff window that found
     * the row missing: the staff member is not the owner the items belong to, and it is how staff fill a vault the
     * owner has not opened yet. Records {@code writer} as the row's writer and returns the row's stamp after the write.
     * A vault this brings back records no payment, so deleting it again refunds nothing.
     */
    public VaultStamp keep(PlayerRef owner, Vault vault, VaultContents newContents, Instant at, String writer) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(newContents, "newContents");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(writer, "writer");
        Vault.Change change = vault.withContents(owner, newContents, at);
        VaultStamp written = repository.save(change.result(), writer);
        events.publish(change.event());
        return written;
    }
}

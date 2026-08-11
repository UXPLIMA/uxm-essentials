package com.uxplima.uxmessentials.vaults.adapter.outbound.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmVaultsActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.application.DeleteVault;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.RenameVault;
import com.uxplima.uxmessentials.vaults.application.SetVaultIcon;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The published vault writes, over the same use cases {@code /vault} runs.
 *
 * <p>Which means the same rules: the amount quota gates a vault that does not exist yet, a configured fee is
 * charged and a configured refund is paid, and the owner is told what happened in their own language. Going around
 * them would mean a plugin could hand a player a vault the plugin's own selector will not draw.
 *
 * <p>The open is the one that has to move between threads. Resolving the vault reads the database, which a tick
 * thread may not do; showing the window touches the player, which only their own thread may do. So it reads on a
 * worker, hops to the player, and completes once the window is up. The other three never leave the worker.
 */
@NullMarked
public final class VaultActions implements UxmVaultsActions {

    private final OpenVault openVault;
    private final DeleteVault deleteVault;
    private final RenameVault renameVault;
    private final SetVaultIcon setVaultIcon;
    private final VaultView view;
    private final boolean allowCustomIcon;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public VaultActions(
            OpenVault openVault,
            DeleteVault deleteVault,
            RenameVault renameVault,
            SetVaultIcon setVaultIcon,
            VaultView view,
            boolean allowCustomIcon,
            PlayerLookup players,
            Scheduler scheduler) {
        this.openVault = Objects.requireNonNull(openVault, "openVault");
        this.deleteVault = Objects.requireNonNull(deleteVault, "deleteVault");
        this.renameVault = Objects.requireNonNull(renameVault, "renameVault");
        this.setVaultIcon = Objects.requireNonNull(setVaultIcon, "setVaultIcon");
        this.view = Objects.requireNonNull(view, "view");
        this.allowCustomIcon = allowCustomIcon;
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> open(UUID ownerId, int index) {
        PlayerRef owner = owner(ownerId, index);
        if (!players.isOnline(ownerId)) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "a vault window has to be shown to somebody"));
        }
        return AsyncActions.perform(scheduler, () -> openVault.open(owner, index))
                .thenCompose(resolved -> resolved.isErr()
                        ? CompletableFuture.completedFuture(refusal(resolved.errorOrThrow()))
                        : show(owner, resolved.orElseThrow()));
    }

    @Override
    public CompletableFuture<UxmOutcome> delete(UUID ownerId, int index) {
        PlayerRef owner = owner(ownerId, index);
        return write(() -> deleteVault.delete(owner, index));
    }

    @Override
    public CompletableFuture<UxmOutcome> rename(UUID ownerId, int index, String name) {
        Objects.requireNonNull(name, "name");
        PlayerRef owner = owner(ownerId, index);
        return write(() -> renameVault.rename(owner, index, name));
    }

    @Override
    public CompletableFuture<UxmOutcome> clearName(UUID ownerId, int index) {
        PlayerRef owner = owner(ownerId, index);
        return write(() -> renameVault.rename(owner, index, null));
    }

    @Override
    public CompletableFuture<UxmOutcome> setIcon(UUID ownerId, int index, String material) {
        Objects.requireNonNull(material, "material");
        PlayerRef owner = owner(ownerId, index);
        if (!allowCustomIcon) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.REFUSED, "the operator switched custom vault icons off"));
        }
        @Nullable Material resolved = Material.matchMaterial(material);
        if (resolved == null) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.REFUSED, "no such material: " + material));
        }
        String name = resolved.name();
        return write(() -> setVaultIcon.setIcon(owner, index, name));
    }

    @Override
    public CompletableFuture<UxmOutcome> clearIcon(UUID ownerId, int index) {
        PlayerRef owner = owner(ownerId, index);
        return write(() -> setVaultIcon.setIcon(owner, index, null));
    }

    /** Hop to the owner's own thread and put the window up, which is the half a worker thread may not do. */
    private CompletableFuture<UxmOutcome> show(PlayerRef owner, Vault vault) {
        return AsyncActions.onPlayer(
                scheduler,
                owner,
                () -> {
                    @Nullable Player live = Bukkit.getPlayer(owner.uuid());
                    if (live == null) {
                        return gone();
                    }
                    view.open(live, owner, owner, vault);
                    return UxmOutcome.ok();
                },
                gone());
    }

    private CompletableFuture<UxmOutcome> write(java.util.function.Supplier<Result<Unit, VaultError>> body) {
        return AsyncActions.perform(scheduler, () -> {
            Result<Unit, VaultError> done = body.get();
            return done.isErr() ? refusal(done.errorOrThrow()) : UxmOutcome.ok();
        });
    }

    private PlayerRef owner(UUID ownerId, int index) {
        Objects.requireNonNull(ownerId, "ownerId");
        if (index < 1) {
            throw new IllegalArgumentException("vault numbers count from one: " + index);
        }
        return ApiValues.subject(players, ownerId);
    }

    private static UxmOutcome gone() {
        return UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the player left before the vault could be opened");
    }

    private static UxmOutcome refusal(VaultError error) {
        return switch (error) {
            case AMOUNT_EXCEEDED ->
                UxmOutcome.failed(UxmFailure.REFUSED, "that vault number is past what the owner may open");
            case CANNOT_AFFORD -> UxmOutcome.failed(UxmFailure.INSUFFICIENT_FUNDS, "the owner cannot pay the fee");
            case NONE_OWNED -> UxmOutcome.failed(UxmFailure.NOT_FOUND, "the player has no vaults");
            case DELETE_UNKNOWN, VAULT_UNKNOWN ->
                UxmOutcome.failed(UxmFailure.NOT_FOUND, "the player has no vault under that number");
        };
    }
}

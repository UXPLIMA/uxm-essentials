package com.uxplima.uxmessentials.vaults.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} that carries a vault's identity on the open GUI (docs/10-feature-modules.md
 * §15.11 — "a Bukkit {@code InventoryHolder} carries the {@code Vault} identity"). When the player closes the
 * window the {@code InventoryCloseEvent} carries the inventory whose holder is one of these, so the close
 * listener resolves the owning {@link Vault} and the viewer straight from the holder and writes the live slots
 * through to the DB — no side map keyed by player is needed.
 *
 * <p>The {@code viewer} is who has the window open (the owner, or staff for the admin form); the {@code owner}
 * is whose vault it is — they differ only for {@code /vault <player>}. The held {@link Vault} is the aggregate
 * the window was opened from; it is the {@code lastTouched}/size/id source the save transition starts from.
 */
@NullMarked
public final class VaultInventoryHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private final PlayerRef owner;
    private final Vault vault;

    private @Nullable Inventory inventory;

    public VaultInventoryHolder(PlayerRef viewer, PlayerRef owner, Vault vault) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.vault = Objects.requireNonNull(vault, "vault");
    }

    /** Who has the window open — the owner, or the staff member for the admin form. */
    public PlayerRef viewer() {
        return viewer;
    }

    /** Whose vault this is. */
    public PlayerRef owner() {
        return owner;
    }

    /** The aggregate the window was opened from — the save transition's starting point. */
    public Vault vault() {
        return vault;
    }

    /** Bind the created inventory to this holder; called once when the window is built. */
    void bind(Inventory created) {
        this.inventory = Objects.requireNonNull(created, "created");
    }

    @Override
    public Inventory getInventory() {
        return Objects.requireNonNull(inventory, "inventory not yet bound");
    }
}

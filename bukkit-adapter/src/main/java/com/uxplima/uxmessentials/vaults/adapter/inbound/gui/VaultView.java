package com.uxplima.uxmessentials.vaults.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.adapter.outbound.VaultItemCodec;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.jspecify.annotations.NullMarked;

/**
 * Builds and opens the inventory-holder-based GUI for a vault: a chest-style inventory sized to the vault's
 * resolved {@link VaultSize}, backed by a {@link VaultInventoryHolder} that carries the {@link Vault} identity
 * so the close listener can resolve the owning vault from the holder. The vault's opaque contents are decoded
 * into the slots through {@link VaultItemCodec}, and the title is a {@link MessageKey} rendered in the viewer's
 * locale and parsed into a {@code Component} — never an inline literal.
 *
 * <p>{@link #open} touches the live player, so it must run on the player's region thread; the caller schedules
 * it through the kernel {@code Scheduler}.
 */
@NullMarked
public final class VaultView {

    private final Server server;
    private final Messages messages;
    private final MiniMessage miniMessage;

    public VaultView(Server server, Messages messages) {
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Open {@code owner}'s {@code vault} for {@code player} (the live viewer behind {@code viewer}). Use the
     * owner title when the viewer is the owner and the admin title when staff is auditing someone else.
     */
    public void open(Player player, PlayerRef viewer, PlayerRef owner, Vault vault) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(vault, "vault");
        VaultInventoryHolder holder = new VaultInventoryHolder(viewer, owner, vault);
        Component title = title(viewer, owner, vault.index());
        Inventory inventory = server.createInventory(holder, vault.size().slots(), title);
        holder.bind(inventory);
        ItemStack[] decoded =
                VaultItemCodec.decode(vault.contents(), vault.size().slots());
        inventory.setContents(decoded);
        player.openInventory(inventory);
    }

    private Component title(PlayerRef viewer, PlayerRef owner, int index) {
        boolean adminView = !viewer.uuid().equals(owner.uuid());
        MessageKey key = adminView ? VaultsMessageKey.VAULT_ADMIN_TITLE : VaultsMessageKey.VAULT_TITLE;
        Map<String, String> placeholders = adminView
                ? Map.of("player", owner.name(), "index", Integer.toString(index))
                : Map.of("index", Integer.toString(index));
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }
}

package com.uxplima.uxmessentials.vaults.adapter.inbound.gui;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.adapter.outbound.VaultItemCodec;
import com.uxplima.uxmessentials.vaults.application.CloseVaultWindow;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.VaultWindows;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultItemPolicy;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.StorageGui;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Opens a vault as a uxmLib {@link StorageGui}: a storage menu sized to the vault's resolved {@link VaultSize}
 * that lets the player take, place, and swap freely. The vault's opaque contents are decoded into the slots
 * through {@link VaultItemCodec}, and the title is a {@link MessageKey} rendered in the viewer's locale and
 * parsed into a {@code Component}, never an inline literal.
 *
 * <p>The window's {@code (owner, vault)} identity is captured per open, so the {@link StorageGui#onClose close
 * handler} writes exactly that vault straight to the database; each open window is tracked so {@link #flushAll}
 * can save every still-open vault on module stop (the {@code open-guis=N} the doctor line reports). Each window
 * is persisted at most once: whichever of close-or-flush reaches it first claims it.
 *
 * <p>That identity is also a lock. A vault lives in at most one window at a time, so an owner and the staff
 * member auditing them through {@code /vault <player>} cannot hold it at once: each would seed a GUI from the
 * same stored row and write its own snapshot back on close, and the later close would restore whatever the
 * earlier viewer had already taken out, duplicating it. The second opener is refused with
 * {@code VAULT_ALREADY_OPEN} and the slot frees again the moment the first window closes or is flushed. The
 * viewer already holding the vault is not a second viewer: they may re-open it, and the new window takes over the
 * outgoing window's live items instead of the stored row, so only one copy of them exists ({@link VaultWindows}).
 *
 * <p>The {@link VaultItemPolicy} blacklist is enforced on close: an item whose material is blocked is removed
 * from the vault and returned to the closer (added to their inventory, overflow dropped at their feet), so a
 * configured material can never be stored. A player holding {@code uxmessentials.vault.bypass-blacklist} skips
 * the filter and keeps every item. The filter runs in the close handler, where the live closer is known, so the
 * return and the {@code VAULT_ITEM_BLOCKED} notice happen on that player's region thread.
 *
 * <p>Overflow is rescued on open. The stored contents are decoded in full ({@link VaultItemCodec#decodeAll}),
 * not truncated to the live size, so items left in slots beyond the current {@link VaultSize}, the size quota
 * shrank since the vault was last saved: are visible rather than silently dropped. Only the first {@code size}
 * slots seed the GUI; any occupied overflow slot is handed back to the opener (added to their inventory,
 * remainder dropped at their feet, the same give-or-drop helper the blacklist uses) and counted into one
 * {@code VAULT_OVERFLOW_RETURNED} notice. When overflow is rescued the truncated (live-size) contents are
 * persisted immediately, not just on close, so a crash before the window closes cannot leave the overflow in
 * the DB row for the next open to rescue and hand out a second time. The overflow is gone from the vault and
 * safely with the player, no loss, no dupe. When staff open another player's vault the overflow returns to the
 * staff opener, consistent with the blacklist's admin semantics.
 *
 * <p>A vault deleted while its window is open stays deleted. Closing the window goes through
 * {@link CloseVaultWindow}: the owner's own window writes back with an update only, and when the row is gone the
 * items still in the window go back to the owner through the {@code VaultItemHandBack} (their live inventory, or kept
 * for their next join), never into a recreated row. Only a staff window keeps the upsert.
 *
 * <p>A non-blank {@code open-sound} is played to the opener as the window opens (resolved once at wire time, so
 * the open path never touches the registry). An unknown sound resolves to {@code null} and is simply not played.
 *
 * <p>{@link #open} touches the live player, so it must run on the player's region thread; the caller schedules
 * it through the kernel {@code Scheduler}. The write-back is dispatched async off that same scheduler.
 */
@NullMarked
public final class VaultView {

    private static final String BYPASS_BLACKLIST = DestinationPermissions.VAULT_BYPASS_BLACKLIST;

    private final Messages messages;
    private final MessageSink sink;
    private final SaveVault saveVault;
    private final CloseVaultWindow closeWindow;
    private final Scheduler scheduler;
    private final Permissions permissions;
    private final VaultItemPolicy itemPolicy;
    private final @Nullable Sound openSound;
    private final VaultWindows<StorageGui> windows = new VaultWindows<>();

    public VaultView(
            Messages messages,
            MessageSink sink,
            SaveVault saveVault,
            CloseVaultWindow closeWindow,
            Scheduler scheduler,
            Permissions permissions,
            VaultItemPolicy itemPolicy,
            @Nullable Sound openSound) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.saveVault = Objects.requireNonNull(saveVault, "saveVault");
        this.closeWindow = Objects.requireNonNull(closeWindow, "closeWindow");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.itemPolicy = Objects.requireNonNull(itemPolicy, "itemPolicy");
        this.openSound = openSound;
    }

    /**
     * Open {@code owner}'s {@code vault} for {@code player} (the live viewer behind {@code viewer}). Use the
     * owner title when the viewer is the owner and the admin title when staff is auditing someone else. A vault
     * already held by another viewer is refused with {@code VAULT_ALREADY_OPEN} and nothing is opened.
     */
    public void open(Player player, PlayerRef viewer, PlayerRef owner, Vault vault) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(vault, "vault");
        int size = vault.size().slots();
        StorageGui gui = Guis.storage()
                .title(title(viewer, owner, vault.index()))
                .rows(size / 9)
                .build();
        // Claim the vault before anything is shown or written. A vault open twice at once would be seeded twice
        // from the same row and written back twice on close, so the later close would restore what the earlier
        // viewer had already taken out: an item dupe. Only the viewer already holding it may re-open it, and then
        // the new window starts from their old window's live items, which is never written back on its own.
        Optional<VaultContents> seed =
                windows.claim(viewer, owner, vault, gui, retired -> takeOver(player, viewer, retired));
        if (seed.isEmpty()) {
            notifyAlreadyOpen(viewer, vault.index());
            return;
        }
        // Decode the full contents so a slot beyond the (possibly shrunken) live size is visible: the first `size`
        // slots seed the GUI, any occupied overflow slot is rescued back to the opener.
        ItemStack[] stored = VaultItemCodec.decodeAll(seed.get());
        gui.setContents(stored);
        int rescued = rescueOverflow(player, viewer, stored, size);
        if (rescued > 0) {
            // The overflow is now safely with the player, but the DB row still carries it; persist the truncated
            // (in-range) GUI immediately so a crash before close cannot re-run the rescue and hand it out again.
            persistOpen(owner, vault, gui);
        }
        gui.onClose(event -> windows.release(vault.id(), gui).ifPresent(window -> {
            returnBlockedItems(event.getPlayer(), viewer, gui);
            persistClosed(window);
        }));
        gui.open(player);
        playOpenSound(player);
    }

    /**
     * Return any item left in a slot at or beyond {@code size} to {@code opener}. The live player who opened
     * the window, and notify {@code viewer} once with the rescued count. The size quota shrank below the stored
     * slot count since the last save, so {@code decodeAll} surfaced these out-of-range items; they are handed
     * back (added to the opener's inventory, remainder dropped at their feet) instead of being silently dropped.
     * The caller persists the truncated GUI immediately when this returns a non-zero count, so the overflow is
     * gone from the vault and safely with the player, no loss, no dupe, and no crash window that could re-rescue.
     * A vault whose contents fit the live size has no overflow and triggers no notice. Returns the total item
     * amount handed back, so the caller knows whether an immediate persist is needed.
     */
    private int rescueOverflow(Player opener, PlayerRef viewer, ItemStack[] stored, int size) {
        int returned = 0;
        for (int slot = size; slot < stored.length; slot++) {
            ItemStack stack = stored[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            returnToPlayer(opener, stack);
            returned += stack.getAmount();
        }
        if (returned > 0) {
            notifyOverflow(viewer, returned);
        }
        return returned;
    }

    private void playOpenSound(Player player) {
        if (openSound == null) {
            return;
        }
        // open() already runs on the opener's region thread (the caller schedules it), so play directly.
        player.playSound(Objects.requireNonNull(player.getLocation(), "player location"), openSound, 1.0f, 1.0f);
    }

    /** Save every still-open vault and forget it; called on module stop so none is lost on disable. */
    public void flushAll() {
        windows.releaseAll().forEach(this::persistClosed);
    }

    /** The number of vault windows currently open (the {@code open-guis=N} the doctor line reports). */
    public int openCount() {
        return windows.count();
    }

    /**
     * Take over the viewer's outgoing window {@code retired}: its blacklisted items go back to the player as its
     * close would have done, the rest become the new window's items, and the old window is emptied so no copy of them
     * stays behind in it.
     */
    private VaultContents takeOver(Player player, PlayerRef viewer, StorageGui retired) {
        returnBlockedItems(player, viewer, retired);
        VaultContents live = VaultItemCodec.encode(retired.contents());
        retired.getInventory().clear();
        return live;
    }

    /**
     * Strip blacklisted items from the still-open {@code gui} and hand them back to the {@code closer} before
     * the contents are encoded. A {@code uxmessentials.vault.bypass-blacklist} holder keeps everything. Each
     * blocked stack is cleared from its slot and returned to the closer's inventory, with any overflow dropped
     * at their feet, so no item is lost or duplicated; a non-zero count produces the {@code VAULT_ITEM_BLOCKED}
     * notice. The closer is the live player behind the close event, so this runs on their region thread.
     */
    private void returnBlockedItems(HumanEntity closer, PlayerRef viewer, StorageGui gui) {
        if (itemPolicy.blockedMaterials().isEmpty() || !(closer instanceof Player player)) {
            return;
        }
        if (permissions.has(viewer, BYPASS_BLACKLIST)) {
            return;
        }
        org.bukkit.inventory.Inventory contents = gui.getInventory();
        int returned = 0;
        for (int slot = 0; slot < contents.getSize(); slot++) {
            ItemStack stack = contents.getItem(slot);
            if (stack == null || !itemPolicy.isBlocked(stack.getType().name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            contents.setItem(slot, null);
            returnToPlayer(player, stack);
            returned += stack.getAmount();
        }
        if (returned > 0) {
            notifyBlocked(viewer, returned);
        }
    }

    /** Add {@code stack} to the player's inventory, dropping any overflow at their feet, never vanish it. */
    private void returnToPlayer(Player player, ItemStack stack) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        overflow.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }

    private void notifyBlocked(PlayerRef viewer, int count) {
        // The sink bridges delivery to the viewer's region thread itself, so this needs no extra scheduling.
        sink.deliver(
                viewer,
                messages.resolve(
                        viewer, VaultsMessageKey.VAULT_ITEM_BLOCKED, Map.of("count", Integer.toString(count))));
    }

    private void notifyAlreadyOpen(PlayerRef viewer, int index) {
        // The sink bridges delivery to the viewer's region thread itself, so this needs no extra scheduling.
        sink.deliver(
                viewer,
                messages.resolve(
                        viewer, VaultsMessageKey.VAULT_ALREADY_OPEN, Map.of("index", Integer.toString(index))));
    }

    private void notifyOverflow(PlayerRef viewer, int count) {
        // The sink bridges delivery to the viewer's region thread itself, so this needs no extra scheduling.
        sink.deliver(
                viewer,
                messages.resolve(
                        viewer, VaultsMessageKey.VAULT_OVERFLOW_RETURNED, Map.of("count", Integer.toString(count))));
    }

    /**
     * Write back a window that is still open. A vault deleted meanwhile is left deleted and nothing else happens:
     * the items are still in the window, and its close hands them back.
     */
    private void persistOpen(PlayerRef owner, Vault vault, StorageGui gui) {
        VaultContents contents = VaultItemCodec.encode(gui.contents());
        scheduler.async(() -> saveVault.save(owner, vault, contents));
    }

    /** Write back a window that has closed, or that is still open when the module stops. */
    private void persistClosed(VaultWindows.Window<StorageGui> window) {
        VaultContents contents = VaultItemCodec.encode(window.window().contents());
        scheduler.async(() -> closeWindow.close(window.viewer(), window.owner(), window.vault(), contents));
    }

    private Component title(PlayerRef viewer, PlayerRef owner, int index) {
        boolean adminView = !viewer.uuid().equals(owner.uuid());
        MessageKey key = adminView ? VaultsMessageKey.VAULT_ADMIN_TITLE : VaultsMessageKey.VAULT_TITLE;
        Map<String, String> placeholders = adminView
                ? Map.of("player", owner.name(), "index", Integer.toString(index))
                : Map.of("index", Integer.toString(index));
        // Centred and bare, like every other window title: a vault is a real container, so it does not pass through
        // the menu engine that would otherwise do it.
        return StyledText.title(messages.resolve(viewer, key, placeholders));
    }
}

package com.uxplima.uxmessentials.vaults.adapter.inbound.gui;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.window.WindowShown;
import com.uxplima.uxmessentials.shared.adapter.outbound.PlayerThread;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.vaults.adapter.outbound.VaultItemCodec;
import com.uxplima.uxmessentials.vaults.application.VaultWindows;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultItemPolicy;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import com.uxplima.uxmessentials.vaults.domain.VaultsMessageKey;
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
 * configured material can never be stored. A player holding {@code uxmessentials.bypass.blacklist.vault} skips
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
 * <p>Closing a window hands its items to {@link VaultWindows}, which keeps them as the vault's pending state until
 * the write lands, so a reopen in the same tick is filled with them rather than with a cached copy from before the
 * close. A vault deleted while its window is open stays deleted: the owner's own window writes back with an update
 * only, and when the row is gone the items go back to the owner (their live inventory, or kept for their next join),
 * never into a recreated row. Only a staff window keeps the upsert. The write writes the items, size and
 * last-touched only, so a rename made while the window was open stays.
 *
 * <p>When the module stops, every open window is closed on its viewer's own thread, so nothing can be taken out of it
 * after its items were written. When the plugin is no longer running (the server is stopping), the scheduler runs
 * nothing more, so the writes run on the calling thread instead.
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
    private final VaultWindows<StorageGui> windows;
    private final Scheduler scheduler;
    private final Permissions permissions;
    private final VaultItemPolicy itemPolicy;
    private final @Nullable Sound openSound;
    private final BooleanSupplier running;
    private final Logger log;

    public VaultView(
            Messages messages,
            MessageSink sink,
            VaultWindows<StorageGui> windows,
            Scheduler scheduler,
            Permissions permissions,
            VaultItemPolicy itemPolicy,
            @Nullable Sound openSound,
            BooleanSupplier running,
            Logger log) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.windows = Objects.requireNonNull(windows, "windows");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.itemPolicy = Objects.requireNonNull(itemPolicy, "itemPolicy");
        this.openSound = openSound;
        this.running = Objects.requireNonNull(running, "running");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Open {@code owner}'s {@code vault} for {@code player} (the live viewer behind {@code viewer}). Use the
     * owner title when the viewer is the owner and the admin title when staff is auditing someone else. A vault
     * already held by another viewer is refused with {@code VAULT_ALREADY_OPEN} and nothing is opened. {@code shown}
     * runs once the window is in front of the player, never for a refused open, so the caller confirms only a vault
     * that really opened.
     *
     * <p>A window another plugin refused keeps its claim until the same viewer opens that vault again, which
     * takes it over. Releasing it here instead would be wrong for a sleeping player, whose window opens a tick late:
     * a second viewer could then claim the same row while the first window was still on its way.
     */
    public void open(Player player, PlayerRef viewer, PlayerRef owner, Vault vault, Runnable shown) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(shown, "shown");
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
            windows.checkpoint(vault.id(), gui, VaultItemCodec.encode(gui.contents()))
                    .ifPresent(this::write);
        }
        gui.onClose(event -> {
            if (!windows.holds(vault.id(), gui)) {
                return; // taken over by a newer window of the same viewer, which writes these items
            }
            returnBlockedItems(event.getPlayer(), viewer, gui);
            windows.release(vault.id(), gui, VaultItemCodec.encode(gui.contents()))
                    .ifPresent(this::write);
        });
        WindowShown.confirm(gui, player, scheduler, shown);
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

    /**
     * Close and save every still-open vault, then wait at most {@code timeout} for the writes, naming the vaults whose
     * items are still at risk when they did not all finish, and logging the items of any write that never landed.
     *
     * <p>The wait blocks the stopping thread only when it is the global thread: the main thread on Paper (whose windows
     * the flush has already closed, leaving only async writes to wait for) or Folia's global region, which runs no
     * player's tasks. On any other thread, a Folia region thread among them, blocking would stall the players it owns,
     * so the wait moves to the async lane and the stop returns; the next instance of the module is then guarded by the
     * row versions, and by these writes still landing.
     */
    public void stop(Duration timeout) {
        flushAll();
        if (scheduler.onGlobalThread() || !running.getAsBoolean()) {
            awaitWrites(timeout);
        } else {
            scheduler.async(() -> awaitWrites(timeout));
        }
    }

    private void awaitWrites(Duration timeout) {
        if (!windows.awaitIdle(timeout)) {
            log.warn(
                    "event=vault_stop_writes_pending after={} vaults={}"
                            + " (their items may be lost if the server stops now)",
                    timeout,
                    windows.unsettled());
        }
        windows.logUnwritten();
    }

    /**
     * Close and save every still-open vault; called on module stop so none is lost. Each window is closed on its
     * viewer's thread so nothing can be taken out of it after its items were written; once the plugin has stopped they
     * are written on this thread, since the scheduler runs nothing more.
     */
    public void flushAll() {
        for (VaultWindows.Window<StorageGui> window : windows.held()) {
            if (running.getAsBoolean()) {
                // Closed right here when this thread owns the viewer (the main thread on Paper), so the stop can wait
                // for the writes; otherwise on the viewer's own thread.
                PlayerThread.run(scheduler, window.viewer(), () -> closeOnViewerThread(window), () -> writeNow(window));
            } else {
                writeNow(window);
            }
        }
    }

    /**
     * Close the window on its viewer's thread; its close handler writes it. A window whose viewer no longer looks at
     * it raises no close, so it is written here instead.
     */
    private void closeOnViewerThread(VaultWindows.Window<StorageGui> window) {
        StorageGui gui = window.window();
        List.copyOf(gui.getInventory().getViewers()).forEach(HumanEntity::closeInventory);
        writeNow(window);
    }

    /** Release and write {@code window} if it still holds its vault, with whatever it holds now. */
    private void writeNow(VaultWindows.Window<StorageGui> window) {
        StorageGui gui = window.window();
        windows.release(window.vault().id(), gui, VaultItemCodec.encode(gui.contents()))
                .ifPresent(this::write);
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
     * the contents are encoded. A {@code uxmessentials.bypass.blacklist.vault} holder keeps everything. Each
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

    /** Run a write off the tick, or on this thread once the plugin has stopped and the scheduler runs nothing. */
    private void write(Runnable write) {
        if (running.getAsBoolean()) {
            scheduler.async(write);
        } else {
            write.run();
        }
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

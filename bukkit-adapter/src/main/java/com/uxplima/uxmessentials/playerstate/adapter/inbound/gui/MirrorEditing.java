package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflinePlayerStorage;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.ContainerLayout;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.OfflineSnapshot;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveResult;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.LivePlayers;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link MirrorEdits} rules bound to the server: items are {@link ItemStack}s, targets and staff are online
 * players, windows are the {@link MirrorWindow} menus and stored containers are the {@link OfflinePlayerStorage}.
 * One instance serves the online and offline views together, which is what makes their editor slot shared.
 *
 * <p>It is also the listener that keeps an edit's items where they are. While a staff member has an editable window
 * open, or its save has not come back, they cannot drop items (unless the item would not fit back into their
 * inventory), move items into or out of a bundle, open another inventory, use, place or eat items, use an armour
 * stand or their crafting grid, or lose their items on death.
 * It tells the rules when a window appears or is refused. It records why a window closed, so a close by disconnecting
 * is saved before the server saves the staff member, and it lets a staff member who leaves with a save still under
 * way finish it first.
 */
@NullMarked
public final class MirrorEditing implements Listener {

    /**
     * Clicks that put items where taking them back cannot reach: dropped on the ground, or into or out of a bundle,
     * whose contents the take-back does not look inside.
     */
    private static final Set<InventoryAction> MOVES_AWAY = Set.copyOf(EnumSet.of(
            InventoryAction.DROP_ALL_CURSOR,
            InventoryAction.DROP_ONE_CURSOR,
            InventoryAction.DROP_ALL_SLOT,
            InventoryAction.DROP_ONE_SLOT,
            InventoryAction.PICKUP_FROM_BUNDLE,
            InventoryAction.PICKUP_ALL_INTO_BUNDLE,
            InventoryAction.PICKUP_SOME_INTO_BUNDLE,
            InventoryAction.PLACE_FROM_BUNDLE,
            InventoryAction.PLACE_ALL_INTO_BUNDLE,
            InventoryAction.PLACE_SOME_INTO_BUNDLE));

    private final MirrorWindow window;
    private final MirrorEdits<ItemStack> edits;
    private final Map<UUID, InventoryCloseEvent.Reason> closing = new ConcurrentHashMap<>();
    private final Set<UUID> leaving = ConcurrentHashMap.newKeySet();

    public MirrorEditing(
            Scheduler scheduler, MirrorWindow window, OfflinePlayerStorage storage, Notifier notifier, Logger log) {
        this.window = Objects.requireNonNull(window, "window");
        Objects.requireNonNull(storage, "storage");
        Server server = new Server();
        this.edits = new MirrorEdits<>(scheduler, new Stacks(), server, server, new Files(storage), notifier, log);
    }

    MirrorEdits<ItemStack> edits() {
        return edits;
    }

    /** Save and close every window; called on module stop. */
    public void stop() {
        edits.stop();
    }

    /** The windows open or being saved. */
    public int openCount() {
        return edits.openCount();
    }

    /** First, so the reason is known when the menu hands the window's contents over. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClosing(InventoryCloseEvent event) {
        closing.put(event.getPlayer().getUniqueId(), event.getReason());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClosed(InventoryCloseEvent event) {
        closing.remove(event.getPlayer().getUniqueId());
    }

    /** First, so a save still under way is finished and settled while the staff member's inventory is still here. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuitting(PlayerQuitEvent event) {
        leaving.add(event.getPlayer().getUniqueId());
        edits.quitting(BukkitRefs.toRef(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        leaving.remove(event.getPlayer().getUniqueId());
    }

    /**
     * A mirror window turning up after its edit was given up on is refused; any other inventory is refused while an
     * edit is open or being saved, so nothing can be put away into it meanwhile.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpening(InventoryOpenEvent event) {
        MirrorHolder mirror = MirrorWindow.of(event.getInventory());
        if (mirror != null) {
            if (!edits.opening(mirror.edit())) {
                event.setCancelled(true);
            }
        } else if (edits.refusesOpen(
                new PlayerRef(event.getPlayer().getUniqueId(), event.getPlayer().getName()))) {
            event.setCancelled(true);
        }
    }

    /** Last, cancelled or not, so the rules know whether a window really appeared. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpened(InventoryOpenEvent event) {
        MirrorHolder mirror = MirrorWindow.of(event.getInventory());
        if (mirror == null) {
            return;
        }
        if (event.isCancelled()) {
            edits.notShown(mirror.edit());
        } else {
            edits.shown(mirror.edit());
        }
    }

    /**
     * Refused only when the item fits back into the inventory, which is where a refused drop goes; the server also
     * drops what a closing window hands back to a full inventory, and refusing that would delete the item.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (edits.refusesDrop(
                BukkitRefs.toRef(event.getPlayer()), event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!edits.holding(event.getWhoClicked().getUniqueId())) {
            return;
        }
        InventoryAction action = event.getAction();
        InventoryType.SlotType type = event.getSlotType();
        if (MOVES_AWAY.contains(action)
                || type == InventoryType.SlotType.OUTSIDE
                || type == InventoryType.SlotType.CRAFTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (edits.holding(event.getPlayer().getUniqueId())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** Also sees {@code PlayerInteractAtEntityEvent}, which has no handler list of its own. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (edits.holding(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Has a handler list of its own, so the handler above does not see it. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (edits.holding(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (edits.holding(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (edits.holding(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Late, so another plugin's drops do not undo it: a staff member who dies mid edit keeps what may be taken back. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (edits.holding(event.getEntity().getUniqueId())) {
            event.setKeepInventory(true);
            event.getDrops().clear();
        }
    }

    /** The window's write-back: hands its contents to the rules with the reason it closed. */
    void closed(MirrorEdits.Edit<ItemStack> edit, @Nullable ItemStack[] contents) {
        UUID viewer = edit.viewer().uuid();
        boolean disconnecting =
                leaving.contains(viewer) || closing.get(viewer) == InventoryCloseEvent.Reason.DISCONNECT;
        edits.closed(
                edit, Arrays.asList(contents), disconnecting ? MirrorEdits.Cause.DISCONNECT : MirrorEdits.Cause.CLOSED);
    }

    private static ContainerLayout layoutOf(MirrorKind kind) {
        return kind == MirrorKind.INVENTORY ? ContainerLayout.INVENTORY : ContainerLayout.ENDER;
    }

    /** Items as the server compares them. */
    private static final class Stacks implements MirrorEdits.Items<ItemStack> {

        @Override
        public boolean nothing(@Nullable ItemStack item) {
            return item == null || item.getType().isAir() || item.getAmount() <= 0;
        }

        @Override
        public boolean same(ItemStack a, ItemStack b) {
            return a.equals(b);
        }

        @Override
        public boolean similar(ItemStack a, ItemStack b) {
            return a.isSimilar(b);
        }

        @Override
        public int amount(ItemStack item) {
            return item.getAmount();
        }

        @Override
        public ItemStack withAmount(ItemStack item, int amount) {
            ItemStack copy = item.clone();
            copy.setAmount(amount);
            return copy;
        }

        @Override
        public String describe(ItemStack item) {
            ItemStack one = item.clone();
            one.setAmount(1);
            return item.getAmount() + "x " + item.getType().getKey() + " item="
                    + Base64.getEncoder().encodeToString(one.serializeAsBytes());
        }
    }

    /** The online players: the targets' live containers and the staff members' inventories and screens. */
    private final class Server implements MirrorEdits.Targets<ItemStack>, MirrorEdits.Viewers<ItemStack> {

        private final Map<MirrorEdits.Edit<ItemStack>, MirrorHolder> holders = new ConcurrentHashMap<>();

        @Override
        public boolean online(PlayerRef player) {
            return LivePlayers.of(player).isPresent();
        }

        @Override
        public List<@Nullable ItemStack> contents(PlayerRef target, MirrorKind kind) {
            Player player = LivePlayers.of(target).orElse(null);
            if (player == null) {
                return Arrays.asList(new ItemStack[kind.slotCount()]);
            }
            return Arrays.asList(
                    kind == MirrorKind.INVENTORY ? InvseeLayout.fromPlayer(player) : EnderLayout.fromPlayer(player));
        }

        @Override
        public @Nullable ItemStack slot(PlayerRef target, MirrorKind kind, int slot) {
            Player player = LivePlayers.of(target).orElse(null);
            if (player == null) {
                return null;
            }
            return kind == MirrorKind.INVENTORY
                    ? InvseeLayout.liveSlot(player, slot)
                    : EnderLayout.liveSlot(player, slot);
        }

        @Override
        public void set(PlayerRef target, MirrorKind kind, int slot, @Nullable ItemStack item) {
            LivePlayers.of(target).ifPresent(player -> {
                if (kind == MirrorKind.INVENTORY) {
                    InvseeLayout.setLiveSlot(player, slot, item);
                } else {
                    EnderLayout.setLiveSlot(player, slot, item);
                }
            });
        }

        @Override
        public List<ItemStack> take(PlayerRef viewer, List<ItemStack> wanted) {
            Player player = LivePlayers.of(viewer).orElse(null);
            if (player == null) {
                return wanted;
            }
            List<ItemStack> missing = new ArrayList<>();
            for (ItemStack item : wanted) {
                int left = takeFromCursor(player, item, item.getAmount());
                left = takeFromInventory(player.getInventory(), item, left);
                if (left > 0) {
                    ItemStack rest = item.clone();
                    rest.setAmount(left);
                    missing.add(rest);
                }
            }
            return missing;
        }

        private int takeFromCursor(HumanEntity player, ItemStack item, int wanted) {
            ItemStack cursor = player.getItemOnCursor();
            if (wanted <= 0 || cursor.getType().isAir() || !cursor.isSimilar(item)) {
                return wanted;
            }
            int taken = Math.min(wanted, cursor.getAmount());
            if (taken == cursor.getAmount()) {
                player.setItemOnCursor(null);
            } else {
                cursor.setAmount(cursor.getAmount() - taken);
                player.setItemOnCursor(cursor);
            }
            return wanted - taken;
        }

        /** Every slot, armour and offhand included: a shift-click out of the window can land in either. */
        private int takeFromInventory(PlayerInventory inventory, ItemStack item, int wanted) {
            int left = wanted;
            @Nullable ItemStack[] slots = inventory.getContents();
            for (int slot = 0; slot < slots.length && left > 0; slot++) {
                ItemStack held = slots[slot];
                if (held == null || held.getType().isAir() || !held.isSimilar(item)) {
                    continue;
                }
                int taken = Math.min(left, held.getAmount());
                left -= taken;
                if (taken == held.getAmount()) {
                    inventory.setItem(slot, null);
                } else {
                    held.setAmount(held.getAmount() - taken);
                    inventory.setItem(slot, held);
                }
            }
            return left;
        }

        @Override
        public void give(PlayerRef viewer, List<ItemStack> owed) {
            LivePlayers.of(viewer).ifPresent(player -> {
                for (ItemStack item : owed) {
                    for (ItemStack stack : stacks(item)) {
                        for (ItemStack rest :
                                player.getInventory().addItem(stack).values()) {
                            player.getWorld().dropItem(player.getLocation(), rest);
                        }
                    }
                }
            });
        }

        private static Collection<ItemStack> stacks(ItemStack item) {
            List<ItemStack> stacks = new ArrayList<>();
            int max = Math.max(1, item.getMaxStackSize());
            for (int left = item.getAmount(); left > 0; left -= max) {
                ItemStack stack = item.clone();
                stack.setAmount(Math.min(max, left));
                stacks.add(stack);
            }
            return stacks;
        }

        @Override
        public boolean fits(PlayerRef viewer, ItemStack item) {
            Player player = LivePlayers.of(viewer).orElse(null);
            if (player == null) {
                return false;
            }
            PlayerInventory inventory = player.getInventory();
            int max = Math.max(1, Math.min(item.getMaxStackSize(), inventory.getMaxStackSize()));
            int room = 0;
            for (ItemStack held : inventory.getStorageContents()) {
                if (held == null || held.getType().isAir()) {
                    room += max;
                } else if (held.isSimilar(item)) {
                    room += Math.max(0, max - held.getAmount());
                }
                if (room >= item.getAmount()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void open(MirrorEdits.Edit<ItemStack> edit) {
            MirrorHolder holder = new MirrorHolder(edit, contents -> {
                holders.remove(edit);
                closed(edit, contents);
            });
            holders.put(edit, holder);
            window.open(holder);
        }

        @Override
        public Optional<List<@Nullable ItemStack>> window(MirrorEdits.Edit<ItemStack> edit) {
            MirrorHolder holder = holders.get(edit);
            return holder == null ? Optional.empty() : window.contents(holder).map(Arrays::asList);
        }

        @Override
        public void close(MirrorEdits.Edit<ItemStack> edit) {
            MirrorHolder holder = holders.remove(edit);
            if (holder != null) {
                window.close(holder);
            }
        }
    }

    /** The stored containers, by window kind. */
    private record Files(OfflinePlayerStorage storage) implements MirrorEdits.Stored<ItemStack> {

        @Override
        public OfflineSnapshot<ItemStack> read(UUID player, MirrorKind kind) {
            return storage.read(player, layoutOf(kind));
        }

        @Override
        public SaveResult save(
                UUID player,
                MirrorKind kind,
                OfflineSnapshot<ItemStack> base,
                Map<Integer, @Nullable ItemStack> changes,
                Predicate<Set<Integer>> partly) {
            return storage.save(player, layoutOf(kind), base, changes, partly);
        }
    }
}

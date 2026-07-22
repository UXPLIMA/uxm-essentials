package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackMessageKey;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Packages a stored snapshot's items into named shulker-box items and gives them to the staff member, so a lost
 * inventory can be inspected or handed back without a live restore. All of a snapshot's stores are exported: the
 * main inventory (hotbar, storage, armor, offhand) and the ender chest; every non-empty stack is packed 27 to a
 * shulker, so an inventory that overflows one box produces as many boxes as it needs. An item that is itself a
 * shulker box is handed over as-is rather than nested (vanilla forbids a box inside a box), matching how
 * AxInventoryRestore exports.
 *
 * <p>The whole flow runs on the staff member's own entity thread through the injected {@link Scheduler}: it
 * deserializes the payload, mints the shulkers, and adds them to the live inventory there (Folia-safe), dropping
 * any that do not fit at the staff member's feet. The target need not be online: the export reads only the stored
 * snapshot and touches only the staff member's inventory.
 */
@NullMarked
public final class SnapshotExporter {

    /** A shulker box holds a single row-of-three grid: 27 slots. */
    private static final int SHULKER_CAPACITY = 27;

    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink messageSink;

    public SnapshotExporter(Scheduler scheduler, Messages messages, MessageSink messageSink) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
    }

    /** Export {@code snapshot} (owned by {@code target}) to shulkers and give them to {@code staff}. */
    public void export(PlayerRef staff, PlayerRef target, Snapshot snapshot) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        scheduler.onEntity(staff, () -> exportOnThread(staff, target, snapshot));
    }

    private void exportOnThread(PlayerRef staff, PlayerRef target, Snapshot snapshot) {
        Player live = Bukkit.getPlayer(staff.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        InventorySnapshotCodec.Decoded decoded = InventorySnapshotCodec.decode(snapshot.contents());
        List<ItemStack> items = nonEmpty(decoded);
        if (items.isEmpty()) {
            notify(staff, InvrollbackMessageKey.INVROLLBACK_NOTHING_TO_EXPORT, Map.of("player", target.name()));
            return;
        }
        List<ItemStack> boxes = pack(staff, target, snapshot, items);
        giveOrDrop(live, boxes);
        notify(
                staff,
                InvrollbackMessageKey.INVROLLBACK_EXPORTED,
                Map.of(
                        "player", target.name(),
                        "cause", snapshot.cause().name(),
                        "count", Integer.toString(boxes.size())));
    }

    /** Every non-empty stack across the snapshot's main inventory and ender chest, as clones the caller owns. */
    private static List<ItemStack> nonEmpty(InventorySnapshotCodec.Decoded decoded) {
        List<ItemStack> items = new ArrayList<>();
        collectInto(decoded.contents(), items);
        collectInto(decoded.enderChest(), items);
        return items;
    }

    private static void collectInto(@Nullable ItemStack[] slots, List<ItemStack> out) {
        for (ItemStack stack : slots) {
            if (stack != null && !stack.getType().isAir()) {
                out.add(stack.clone());
            }
        }
    }

    /**
     * Pack {@code items} into shulker boxes: a stack that is itself a shulker box is passed through untouched
     * (never nested), the rest fill boxes 27 stacks at a time. Each minted box is named and lore-tagged for the
     * player, cause, time, and its part index.
     */
    private List<ItemStack> pack(PlayerRef staff, PlayerRef target, Snapshot snapshot, List<ItemStack> items) {
        List<ItemStack> passthrough = new ArrayList<>();
        List<ItemStack> packable = new ArrayList<>();
        for (ItemStack item : items) {
            if (Tag.SHULKER_BOXES.isTagged(item.getType())) {
                passthrough.add(item);
            } else {
                packable.add(item);
            }
        }
        List<List<ItemStack>> groups = new ArrayList<>();
        for (int from = 0; from < packable.size(); from += SHULKER_CAPACITY) {
            groups.add(packable.subList(from, Math.min(from + SHULKER_CAPACITY, packable.size())));
        }
        List<ItemStack> boxes = new ArrayList<>(passthrough);
        for (int index = 0; index < groups.size(); index++) {
            boxes.add(shulker(staff, target, snapshot, index + 1, groups.size(), groups.get(index)));
        }
        return boxes;
    }

    private ItemStack shulker(
            PlayerRef staff, PlayerRef target, Snapshot snapshot, int part, int parts, List<ItemStack> contents) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.name());
        placeholders.put("cause", snapshot.cause().name());
        placeholders.put("time", SnapshotDisplay.time(snapshot.createdAt()));
        placeholders.put("part", Integer.toString(part));
        placeholders.put("parts", Integer.toString(parts));
        ItemStack box = ItemBuilder.of(Material.SHULKER_BOX)
                .name(text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_SHULKER_NAME, placeholders))
                .lore(text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_SHULKER_LORE, placeholders))
                .build();
        if (box.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof ShulkerBox state) {
            state.getInventory().setContents(contents.toArray(new ItemStack[0]));
            meta.setBlockState(state);
            box.setItemMeta(meta);
        }
        return box;
    }

    /** Add the boxes to the staff member's inventory, dropping any that do not fit at their feet. */
    private static void giveOrDrop(Player live, List<ItemStack> boxes) {
        Map<Integer, ItemStack> overflow = live.getInventory().addItem(boxes.toArray(new ItemStack[0]));
        for (ItemStack leftover : overflow.values()) {
            live.getWorld().dropItemNaturally(live.getLocation(), leftover);
        }
    }

    private void notify(PlayerRef staff, MessageKey key, Map<String, String> placeholders) {
        messageSink.deliver(staff, messages.resolve(staff, key, placeholders));
    }

    private Component text(PlayerRef staff, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(staff, key, placeholders));
    }
}

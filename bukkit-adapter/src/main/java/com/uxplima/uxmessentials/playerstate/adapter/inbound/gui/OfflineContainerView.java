package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.OfflineHolder.Kind;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflineInventory;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflinePlayerStorage;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Opens {@code /invsee} and {@code /endersee} against an <em>offline</em> target. The target's stored items are
 * read from disk through {@link OfflinePlayerStorage} on the async lane, then a managed menu — the same dupe-safe
 * private copy the online {@link InvseeView} uses (54-slot invsee layout, or a 27-slot ender chest) — is opened on
 * the viewer's entity thread and seeded from that snapshot. The viewer edits the copy; on close the edits are
 * reconciled back.
 *
 * <p>The write-back resolves the login race: if the target is still offline, the edit is written to their
 * {@code playerdata} file on the async lane; if they have logged in while the menu was open, it is applied to
 * their live inventory on their own entity thread instead (a disk write would be lost under their live session).
 * Offline {@code /invsee} respects the {@code uxmessentials.invsee.modify} gate just like the online view; offline
 * {@code /endersee} is editable, mirroring the live ender-chest open. Every open window is tracked so a single
 * write-back claims it — whichever of the close handler or {@link #flushAll} (on module stop) reaches it first.
 */
@NullMarked
public final class OfflineContainerView {

    private static final String MODIFY_PERMISSION = "uxmessentials.invsee.modify";

    private final Messages messages;
    private final Scheduler scheduler;
    private final OfflinePlayerStorage storage;
    private final Set<OfflineHolder> open = ConcurrentHashMap.newKeySet();

    public OfflineContainerView(Messages messages, Scheduler scheduler, OfflinePlayerStorage storage) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    /** Open {@code subject}'s stored inventory for {@code viewer}. */
    public void openInventory(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        loadThen(viewer, subject, (looker, snapshot) -> openInventoryMenu(looker, subject, snapshot));
    }

    /** Open {@code subject}'s stored ender chest for {@code viewer}. */
    public void openEnderChest(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        loadThen(viewer, subject, (looker, snapshot) -> openEnderMenu(looker, subject, snapshot));
    }

    /** Write back and forget every still-open offline menu; called on module stop so no edit is lost on disable. */
    public void flushAll() {
        for (OfflineHolder holder : Set.copyOf(open)) {
            if (open.remove(holder)) {
                reconcile(holder);
            }
        }
    }

    /** The number of offline storage menus currently open. */
    public int openCount() {
        return open.size();
    }

    /** Claim {@code holder}'s window on close and reconcile it back. Called by the listener. */
    void onClose(OfflineHolder holder) {
        Objects.requireNonNull(holder, "holder");
        if (open.remove(holder)) {
            reconcile(holder);
        }
    }

    private void loadThen(PlayerRef viewer, PlayerRef subject, BiConsumer<Player, OfflineInventory> opener) {
        scheduler.async(() -> storage.load(subject.uuid())
                .ifPresent(snapshot -> scheduler.onEntity(viewer, () -> {
                    Player looker = Bukkit.getPlayer(viewer.uuid());
                    if (looker != null && looker.isOnline()) {
                        opener.accept(looker, snapshot);
                    }
                })));
    }

    private void openInventoryMenu(Player looker, PlayerRef subject, OfflineInventory snapshot) {
        OfflineHolder holder = new OfflineHolder(subject, looker.hasPermission(MODIFY_PERMISSION), Kind.INVENTORY);
        Inventory menu = Bukkit.createInventory(
                holder, InvseeLayout.SIZE, title(looker, subject, PlayerstateMessageKey.INVSEE_TITLE));
        holder.attach(menu);
        InvseeLayout.seedSlots(menu, snapshot.slots());
        open.add(holder);
        looker.openInventory(menu);
    }

    private void openEnderMenu(Player looker, PlayerRef subject, OfflineInventory snapshot) {
        OfflineHolder holder = new OfflineHolder(subject, true, Kind.ENDER);
        Inventory menu = Bukkit.createInventory(
                holder, OfflineInventory.ENDER_SIZE, title(looker, subject, PlayerstateMessageKey.ENDERSEE_TITLE));
        holder.attach(menu);
        EnderLayout.seedSlots(menu, snapshot.ender());
        open.add(holder);
        looker.openInventory(menu);
    }

    private void reconcile(OfflineHolder holder) {
        if (!holder.editable()) {
            return;
        }
        Inventory menu = holder.getInventory();
        if (holder.kind() == Kind.INVENTORY) {
            persistInventory(holder.target(), InvseeLayout.readSlots(menu));
        } else {
            persistEnder(holder.target(), EnderLayout.readSlots(menu));
        }
    }

    private void persistInventory(PlayerRef subject, @Nullable ItemStack[] slots) {
        Player live = Bukkit.getPlayer(subject.uuid());
        if (live != null && live.isOnline()) {
            scheduler.onEntity(subject, () -> {
                Player target = Bukkit.getPlayer(subject.uuid());
                if (target != null && target.isOnline()) {
                    InvseeLayout.applySlots(slots, target);
                }
            });
        } else {
            scheduler.async(() -> storage.saveInventory(subject.uuid(), slots));
        }
    }

    private void persistEnder(PlayerRef subject, @Nullable ItemStack[] ender) {
        Player live = Bukkit.getPlayer(subject.uuid());
        if (live != null && live.isOnline()) {
            scheduler.onEntity(subject, () -> {
                Player target = Bukkit.getPlayer(subject.uuid());
                if (target != null && target.isOnline()) {
                    EnderLayout.applySlots(ender, target);
                }
            });
        } else {
            scheduler.async(() -> storage.saveEnderChest(subject.uuid(), ender));
        }
    }

    private Component title(Player viewer, PlayerRef subject, MessageKey key) {
        PlayerRef looker = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        String rendered = messages.resolve(looker, key, Map.of("player", subject.name()));
        return StyledText.render(rendered);
    }
}

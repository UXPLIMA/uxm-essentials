package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Opens {@code /invsee} as a managed 54-slot menu that mirrors a target's full inventory — main slots, armour,
 * and offhand (see {@link InvseeLayout}) — and reconciles the viewer's edits back onto the target when the menu
 * closes. The viewer edits this private copy, never the target's live {@link org.bukkit.inventory.PlayerInventory}
 * object, which is what closes the classic raw-inventory dupe window: a shift-click or cursor move shuffles items
 * inside the copy only, and {@link InvseeLayout#writeBack} applies the final state in one pass on close.
 *
 * <p>The open runs on the viewer's entity thread (the menu lives in their screen); the write-back runs on the
 * target's entity thread (it mutates the target's entity), each through the kernel {@link Scheduler}. Every open
 * window is tracked so a single write-back claims it — whichever of the close handler or {@link #flushAll} (on
 * module stop) reaches it first — and a still-open window is never written back twice. A target who logged off
 * before the close drops their write-back silently; the menu copy is simply discarded.
 */
@NullMarked
public final class InvseeView {

    private static final String MODIFY_PERMISSION = "uxmessentials.invsee.modify";

    private final Messages messages;
    private final Scheduler scheduler;
    private final Set<InvseeHolder> open = ConcurrentHashMap.newKeySet();

    public InvseeView(Messages messages, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Open {@code subject}'s inventory for {@code viewer}. The target's items are snapshotted on the target's own
     * entity thread — on Folia the target's live inventory is owned by that region thread, so reading it from the
     * viewer's thread is the asymmetric unsafe half this fix removes — and the menu is then built and opened on the
     * viewer's entity thread from that snapshot. The open is skipped when either player has gone offline. The
     * viewer's edit right is the {@code uxmessentials.invsee.modify} node read off the live viewer there — without
     * it the menu opens view-only (every click cancelled).
     */
    public void open(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        scheduler.onEntity(subject, () -> {
            Player target = Bukkit.getPlayer(subject.uuid());
            if (target == null || !target.isOnline()) {
                return;
            }
            @Nullable ItemStack[] snapshot = InvseeLayout.fromPlayer(target);
            scheduler.onEntity(viewer, () -> {
                Player looker = Bukkit.getPlayer(viewer.uuid());
                if (looker != null && looker.isOnline()) {
                    openResolved(looker, subject, snapshot, looker.hasPermission(MODIFY_PERMISSION));
                }
            });
        });
    }

    /** Write back and forget every still-open menu; called on module stop so no edit is lost on disable. */
    public void flushAll() {
        for (InvseeHolder holder : Set.copyOf(open)) {
            if (open.remove(holder)) {
                persist(holder);
            }
        }
    }

    /** The number of invsee menus currently open (the {@code open-guis=N} a doctor line could report). */
    public int openCount() {
        return open.size();
    }

    /** Claim {@code holder}'s window on close and reconcile it back onto the target. Called by the listener. */
    void onClose(InvseeHolder holder) {
        Objects.requireNonNull(holder, "holder");
        if (open.remove(holder)) {
            persist(holder);
        }
    }

    private void openResolved(Player looker, PlayerRef subject, @Nullable ItemStack[] snapshot, boolean editable) {
        InvseeHolder holder = new InvseeHolder(subject, editable);
        Inventory menu = Bukkit.createInventory(holder, InvseeLayout.SIZE, title(looker, subject));
        holder.attach(menu);
        InvseeLayout.seedSlots(menu, snapshot);
        open.add(holder);
        looker.openInventory(menu);
    }

    private void persist(InvseeHolder holder) {
        PlayerRef subject = holder.target();
        scheduler.onEntity(subject, () -> {
            Player target = Bukkit.getPlayer(subject.uuid());
            if (target != null && target.isOnline()) {
                InvseeLayout.writeBack(holder.getInventory(), target);
            }
        });
    }

    private Component title(Player viewer, PlayerRef subject) {
        PlayerRef looker = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        String rendered =
                messages.resolve(looker, PlayerstateMessageKey.INVSEE_TITLE, Map.of("player", subject.name()));
        return StyledText.render(rendered);
    }
}

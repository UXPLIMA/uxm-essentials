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
 * Opens an online {@code /endersee} as a managed 27-slot menu that mirrors a target's ender chest (see {@link
 * EnderLayout}) and reconciles the viewer's edits back onto the target when the menu closes. The viewer edits this
 * private copy, never the target's live {@link Player#getEnderChest()} object: handing the viewer the live
 * container would let every subsequent click read and write that foreign container from the viewer's region thread,
 * which on Folia is the cross-region hazard this view removes. A relocation inside the copy shuffles items in the
 * copy only, and {@link EnderLayout#writeBack} applies the final state in one pass on close.
 *
 * <p>The open runs on the viewer's entity thread (the menu lives in their screen); the target's contents are
 * snapshotted first on the target's own entity thread (on Folia the live ender chest is owned by that region
 * thread). The write-back runs on the target's entity thread (it mutates the target's entity), each through the
 * kernel {@link Scheduler}. Every open window is tracked so a single write-back claims it — whichever of the close
 * handler or {@link #flushAll} (on module stop) reaches it first — and a still-open window is never written back
 * twice. A target who logged off before the close drops their write-back silently; the menu copy is discarded.
 *
 * <p>This is the ender-chest mirror of {@link InvseeView}; online {@code /endersee} is always editable, matching
 * the offline {@code /endersee} path.
 */
@NullMarked
public final class EnderseeView {

    private final Messages messages;
    private final Scheduler scheduler;
    private final Set<EnderseeHolder> open = ConcurrentHashMap.newKeySet();

    public EnderseeView(Messages messages, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Open {@code subject}'s ender chest for {@code viewer}. The target's contents are snapshotted on the target's
     * own entity thread — on Folia the live ender chest is owned by that region thread, so reading it from the
     * viewer's thread is the asymmetric unsafe half this fix removes — and the menu is then built and opened on the
     * viewer's entity thread from that snapshot. The open is skipped when either player has gone offline.
     */
    public void open(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        scheduler.onEntity(subject, () -> {
            Player target = Bukkit.getPlayer(subject.uuid());
            if (target == null || !target.isOnline()) {
                return;
            }
            @Nullable ItemStack[] snapshot = EnderLayout.fromPlayer(target);
            scheduler.onEntity(viewer, () -> {
                Player looker = Bukkit.getPlayer(viewer.uuid());
                if (looker != null && looker.isOnline()) {
                    openResolved(looker, subject, snapshot);
                }
            });
        });
    }

    /** Write back and forget every still-open menu; called on module stop so no edit is lost on disable. */
    public void flushAll() {
        for (EnderseeHolder holder : Set.copyOf(open)) {
            if (open.remove(holder)) {
                persist(holder);
            }
        }
    }

    /** The number of endersee menus currently open. */
    public int openCount() {
        return open.size();
    }

    /** Claim {@code holder}'s window on close and reconcile it back onto the target. Called by the listener. */
    void onClose(EnderseeHolder holder) {
        Objects.requireNonNull(holder, "holder");
        if (open.remove(holder)) {
            persist(holder);
        }
    }

    private void openResolved(Player looker, PlayerRef subject, @Nullable ItemStack[] snapshot) {
        EnderseeHolder holder = new EnderseeHolder(subject);
        Inventory menu = Bukkit.createInventory(holder, EnderLayout.SIZE, title(looker, subject));
        holder.attach(menu);
        EnderLayout.seedSlots(menu, snapshot);
        open.add(holder);
        looker.openInventory(menu);
    }

    private void persist(EnderseeHolder holder) {
        PlayerRef subject = holder.target();
        scheduler.onEntity(subject, () -> {
            Player target = Bukkit.getPlayer(subject.uuid());
            if (target != null && target.isOnline()) {
                EnderLayout.writeBack(holder.getInventory(), target);
            }
        });
    }

    private Component title(Player viewer, PlayerRef subject) {
        PlayerRef looker = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        String rendered =
                messages.resolve(looker, PlayerstateMessageKey.ENDERSEE_TITLE, Map.of("player", subject.name()));
        return StyledText.render(rendered);
    }
}

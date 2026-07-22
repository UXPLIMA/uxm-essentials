package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackMessageKey;
import com.uxplima.uxmessentials.invrollback.application.RestoreSnapshot;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Applies a chosen snapshot to a target's live inventory — the action behind the {@code /invrestore} preview's
 * restore button. The flow hops three times so every touch happens on the thread that owns it (Folia-safe): on the
 * target's own entity thread it reads and serializes the target's <em>current</em> inventory (main + armor +
 * offhand + ender chest) as the pre-restore state; off the tick thread the {@link RestoreSnapshot} use case freezes
 * that state as a {@link SnapshotCause#RESTORE} safety snapshot and resolves the chosen snapshot; back on the
 * target's entity thread the chosen snapshot is decoded and set onto the live inventory.
 *
 * <p>Restore requires the target <b>online</b> — the snapshot is applied to their live inventory, never written to
 * disk — so a target who has logged off (between the GUI open and the click, or ever) yields a "not online" line to
 * the staff member and no change; their snapshots persist, so the restore succeeds once they rejoin. A stale
 * snapshot id (pruned or already restored) resolves to nothing and applies no change. The staff confirmation and
 * the offline refusal are delivered to the staff member through the region-hopping {@link MessageSink}.
 */
@NullMarked
public final class SnapshotRestorer {

    private final RestoreSnapshot restoreSnapshot;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink messageSink;
    private final Clock clock;

    public SnapshotRestorer(
            RestoreSnapshot restoreSnapshot,
            Scheduler scheduler,
            Messages messages,
            MessageSink messageSink,
            Clock clock) {
        this.restoreSnapshot = Objects.requireNonNull(restoreSnapshot, "restoreSnapshot");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Restore {@code target}'s inventory from snapshot {@code id}, safety-snapshotting first; notify {@code staff}. */
    public void restore(PlayerRef staff, PlayerRef target, SnapshotId id) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(id, "id");
        scheduler.onEntity(target, () -> readCurrentThenRestore(staff, target, id));
    }

    /** On the target's entity thread: serialize the current inventory, then run the restore off the tick thread. */
    private void readCurrentThenRestore(PlayerRef staff, PlayerRef target, SnapshotId id) {
        Player live = Bukkit.getPlayer(target.uuid());
        if (live == null || !live.isOnline()) {
            notify(staff, InvrollbackMessageKey.INVROLLBACK_PLAYER_NOT_FOUND, Map.of("player", target.name()));
            return;
        }
        byte[] current = InventorySnapshotCodec.encode(
                live.getInventory().getContents(),
                live.getEnderChest().getContents(),
                BukkitRefs.toPosition(Objects.requireNonNull(live.getLocation(), "target location")));
        Instant now = clock.instant();
        scheduler.async(() -> {
            Optional<Snapshot> chosen = restoreSnapshot.restore(target.uuid(), id, current, now);
            chosen.ifPresent(snapshot -> scheduler.onEntity(target, () -> apply(staff, target, snapshot)));
        });
    }

    /** On the target's entity thread: decode the chosen snapshot and set it onto the live inventory. */
    private void apply(PlayerRef staff, PlayerRef target, Snapshot snapshot) {
        Player live = Bukkit.getPlayer(target.uuid());
        if (live == null || !live.isOnline()) {
            notify(staff, InvrollbackMessageKey.INVROLLBACK_PLAYER_NOT_FOUND, Map.of("player", target.name()));
            return;
        }
        InventorySnapshotCodec.Decoded decoded = InventorySnapshotCodec.decode(snapshot.contents());
        live.getInventory().setContents(decoded.contents());
        if (decoded.enderChest().length > 0) {
            live.getEnderChest().setContents(decoded.enderChest());
        }
        notify(
                staff,
                InvrollbackMessageKey.INVROLLBACK_RESTORED,
                Map.of("player", target.name(), "cause", snapshot.cause().name()));
    }

    private void notify(PlayerRef staff, MessageKey key, Map<String, String> placeholders) {
        messageSink.deliver(staff, messages.resolve(staff, key, placeholders));
    }
}

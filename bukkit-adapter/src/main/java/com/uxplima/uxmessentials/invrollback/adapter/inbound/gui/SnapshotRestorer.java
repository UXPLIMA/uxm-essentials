package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.application.RestoreSnapshot;
import com.uxplima.uxmessentials.invrollback.domain.InvrollbackMessageKey;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.invrollback.domain.event.SnapshotRestored;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * Applies a chosen snapshot to a target's live inventory. The action behind the {@code /invrestore} preview's
 * restore button. The flow hops three times so every touch happens on the thread that owns it (Folia-safe): on the
 * target's own entity thread it reads and serializes the target's <em>current</em> inventory (main + armor +
 * offhand + ender chest) as the pre-restore state; off the tick thread the {@link RestoreSnapshot} use case freezes
 * that state as a {@link SnapshotCause#RESTORE} safety snapshot and resolves the chosen snapshot; back on the
 * target's entity thread the chosen snapshot is decoded and set onto the live inventory.
 *
 * <p>The snapshot is applied to the target's live inventory, which only exists on the server they are on. For a
 * target who is not online here, offline or on another server, the restore staff chose is kept in the database
 * with a copy of the snapshot, and put on them as they next join a server of it, or at once by the server they are
 * on when the kept restore is announced there ({@link #applyKept}), with the same safety copy first. A stale
 * snapshot id (pruned or already restored) resolves to nothing and applies no change. The staff confirmation is
 * delivered to the staff member through the region-hopping {@link MessageSink}.
 */
@NullMarked
public final class SnapshotRestorer {

    private final RestoreSnapshot restoreSnapshot;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink messageSink;
    private final Clock clock;
    private final DomainEventPublisher events;

    public SnapshotRestorer(
            RestoreSnapshot restoreSnapshot,
            Scheduler scheduler,
            Messages messages,
            MessageSink messageSink,
            Clock clock,
            DomainEventPublisher events) {
        this.restoreSnapshot = Objects.requireNonNull(restoreSnapshot, "restoreSnapshot");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * Restore {@code target}'s inventory from snapshot {@code id}, safety-snapshotting first; notify {@code staff}. A
     * target who is not online here gets the restore as they next join a server of the database.
     */
    public void restore(PlayerRef staff, PlayerRef target, SnapshotId id) {
        Objects.requireNonNull(staff, "staff");
        restore(target, id, true, outcome -> report(staff, target, outcome));
    }

    /**
     * The same restore, reporting what happened rather than telling a staff member about it, and only for a target
     * online here: a caller that asks for a restore now is told the target is away rather than given a later one.
     *
     * <p>The published action needs the three answers this flow can produce, and the flow itself is three thread
     * hops long, so it is shared rather than written a second time: a second copy would be the one that forgets
     * the safety snapshot.
     */
    public void restore(PlayerRef target, SnapshotId id, Consumer<Outcome> whenDone) {
        restore(target, id, false, whenDone);
    }

    private void restore(PlayerRef target, SnapshotId id, boolean keepWhenAway, Consumer<Outcome> whenDone) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(whenDone, "whenDone");
        Runnable away = keepWhenAway
                ? () -> scheduler.async(() -> whenDone.accept(
                        restoreSnapshot.keepForNextJoin(target.uuid(), id).isPresent()
                                ? Outcome.KEPT
                                : Outcome.SNAPSHOT_GONE))
                : () -> whenDone.accept(Outcome.TARGET_OFFLINE);
        scheduler.onEntity(target, () -> readCurrentThenRestore(target, id, keepWhenAway, away, whenDone), away);
    }

    /** On the target's entity thread: serialize the current inventory, then run the restore off the tick thread. */
    private void readCurrentThenRestore(
            PlayerRef target, SnapshotId id, boolean keepWhenAway, Runnable away, Consumer<Outcome> whenDone) {
        Optional<Player> live = live(target);
        if (live.isEmpty()) {
            away.run();
            return;
        }
        byte[] current = current(live.get());
        Instant now = clock.instant();
        scheduler.async(() -> {
            Optional<Snapshot> chosen = restoreSnapshot.restore(target.uuid(), id, current, now);
            if (chosen.isEmpty()) {
                whenDone.accept(Outcome.SNAPSHOT_GONE);
                return;
            }
            Snapshot snapshot = chosen.orElseThrow();
            // The safety copy is taken; a target who left since gets the chosen snapshot at their next join instead.
            Runnable left = keepWhenAway
                    ? () -> scheduler.async(() -> {
                        restoreSnapshot.keepAgain(target.uuid(), List.of(snapshot));
                        whenDone.accept(Outcome.KEPT);
                    })
                    : () -> whenDone.accept(Outcome.TARGET_OFFLINE);
            scheduler.onEntity(
                    target,
                    () -> {
                        if (apply(target, snapshot)) {
                            whenDone.accept(new Outcome.Restored(snapshot.cause()));
                        } else {
                            left.run();
                        }
                    },
                    left);
        });
    }

    /**
     * Put the restores kept for {@code target} on them, for a target who just joined or is online here when another
     * server kept one. Off the tick the kept restores are taken, which removes them; on the target's thread their
     * current inventory is read for the safety copy; then the newest kept restore is put on them, being the last one
     * staff chose. A target who left before that keeps every taken restore for their next join.
     */
    public void applyKept(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        scheduler.async(() -> {
            List<Snapshot> kept = restoreSnapshot.takeKept(target.uuid());
            if (kept.isEmpty()) {
                return;
            }
            Runnable keepAgain = () -> scheduler.async(() -> restoreSnapshot.keepAgain(target.uuid(), kept));
            Snapshot newest = kept.get(kept.size() - 1);
            scheduler.onEntity(
                    target,
                    () -> {
                        Optional<Player> live = live(target);
                        if (live.isEmpty()) {
                            keepAgain.run();
                            return;
                        }
                        byte[] current = current(live.get());
                        Instant now = clock.instant();
                        scheduler.async(() -> {
                            restoreSnapshot.safetyCopy(target.uuid(), current, now);
                            scheduler.onEntity(
                                    target,
                                    () -> {
                                        if (!apply(target, newest)) {
                                            keepAgain.run();
                                        }
                                    },
                                    keepAgain);
                        });
                    },
                    keepAgain);
        });
    }

    /**
     * On the target's entity thread: decode {@code snapshot} and set it onto the live inventory. Returns false, with
     * nothing changed, when the target is no longer online.
     */
    private boolean apply(PlayerRef target, Snapshot snapshot) {
        Optional<Player> live = live(target);
        if (live.isEmpty()) {
            return false;
        }
        InventorySnapshotCodec.Decoded decoded = InventorySnapshotCodec.decode(snapshot.contents());
        live.get().getInventory().setContents(decoded.contents());
        if (decoded.enderChest().length > 0) {
            live.get().getEnderChest().setContents(decoded.enderChest());
        }
        // Published after the items are set, so a listener that reads the inventory sees the restored one.
        events.publish(new SnapshotRestored(target, snapshot.id(), snapshot.cause(), snapshot.createdAt()));
        return true;
    }

    private static Optional<Player> live(PlayerRef target) {
        Player live = Bukkit.getPlayer(target.uuid());
        return live == null || !live.isOnline() ? Optional.empty() : Optional.of(live);
    }

    /** The live inventory, ender chest and position of {@code live}, serialized for the safety copy. */
    private static byte[] current(Player live) {
        return InventorySnapshotCodec.encode(
                live.getInventory().getContents(),
                live.getEnderChest().getContents(),
                BukkitRefs.toPosition(Objects.requireNonNull(live.getLocation(), "target location")));
    }

    /** Turn an outcome into the line the staff member who asked for it reads. */
    private void report(PlayerRef staff, PlayerRef target, Outcome outcome) {
        if (outcome instanceof Outcome.Restored restored) {
            notify(
                    staff,
                    InvrollbackMessageKey.INVROLLBACK_RESTORED,
                    Map.of("player", target.name(), "cause", restored.cause().name()));
            return;
        }
        if (outcome instanceof Outcome.Kept) {
            notify(staff, InvrollbackMessageKey.INVROLLBACK_RESTORE_KEPT, Map.of("player", target.name()));
            return;
        }
        // A stale snapshot and an offline target read the same to staff: nothing was restored, and the window they
        // clicked in was drawn before whichever of the two happened.
        notify(staff, InvrollbackMessageKey.INVROLLBACK_PLAYER_NOT_FOUND, Map.of("player", target.name()));
    }

    private void notify(PlayerRef staff, MessageKey key, Map<String, String> placeholders) {
        messageSink.deliver(staff, messages.resolve(staff, key, placeholders));
    }

    /**
     * What a restore did: it happened, it was kept for the target's next join, the target was not there, or the
     * snapshot no longer exists.
     */
    public sealed interface Outcome {

        /** The inventory was overwritten from a snapshot taken for {@code cause}. */
        record Restored(SnapshotCause cause) implements Outcome {
            public Restored {
                Objects.requireNonNull(cause, "cause");
            }
        }

        /** The target is not online, so there was no live inventory to write to. */
        Outcome TARGET_OFFLINE = new Missing();

        /** The target is not online here, so the restore was kept for their next join. */
        Outcome KEPT = new Kept();

        /** The snapshot no longer resolves: pruned, or already restored. */
        Outcome SNAPSHOT_GONE = new Gone();

        /** The target-offline outcome, as a type so the sealed set stays closed. */
        record Missing() implements Outcome {}

        /** The stale-snapshot outcome, as a type so the sealed set stays closed. */
        record Gone() implements Outcome {}

        /** The kept-for-later outcome, as a type so the sealed set stays closed. */
        record Kept() implements Outcome {}
    }
}

package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackMessageKey;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Teleports a staff member to where a snapshot was captured. The location rides inside the snapshot payload, so it
 * is read with the item-free {@link InventorySnapshotCodec#summarize} (safe on any thread); the hop itself runs on
 * the staff member's own entity thread through the injected {@link Scheduler} and uses Paper's {@code teleportAsync}
 * (Folia-safe). A snapshot taken before location capture shipped, or one whose world is no longer loaded, yields a
 * clear refusal and no move.
 *
 * <p>The hop is fire-and-forget: the {@code teleportAsync} future is deliberately not joined (blocking a tick thread
 * on it is forbidden), and the confirmation line is sent optimistically once the hop is dispatched. A thrown
 * {@link RuntimeException} from the dispatch (the MockBukkit stub throws; a real Paper failure would be rare) is a
 * fail-soft one-line operator warning rather than a crash.
 */
@NullMarked
public final class SnapshotTeleporter {

    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink messageSink;
    private final Logger log;

    public SnapshotTeleporter(Scheduler scheduler, Messages messages, MessageSink messageSink, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Teleport {@code staff} to where {@code snapshot} (owned by {@code target}) was captured. */
    public void teleport(PlayerRef staff, PlayerRef target, Snapshot snapshot) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        scheduler.onEntity(staff, () -> teleportOnThread(staff, target, snapshot));
    }

    private void teleportOnThread(PlayerRef staff, PlayerRef target, Snapshot snapshot) {
        Player live = Bukkit.getPlayer(staff.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        Optional<Position> location =
                InventorySnapshotCodec.summarize(snapshot.contents()).location();
        if (location.isEmpty()) {
            notify(staff, InvrollbackMessageKey.INVROLLBACK_NO_LOCATION, Map.of("player", target.name()));
            return;
        }
        Position position = location.get();
        World world = Bukkit.getWorld(position.world().uid());
        if (world == null) {
            notify(
                    staff,
                    InvrollbackMessageKey.INVROLLBACK_WORLD_UNAVAILABLE,
                    Map.of("world", position.world().name()));
            return;
        }
        hop(live, BukkitRefs.toLocation(world, position));
        notify(
                staff,
                InvrollbackMessageKey.INVROLLBACK_TELEPORTED,
                Map.of(
                        "player", target.name(),
                        "cause", snapshot.cause().name(),
                        "location", SnapshotDisplay.label(position)));
    }

    private void hop(Player live, Location destination) {
        try {
            var ignored = live.teleportAsync(destination);
        } catch (RuntimeException failure) {
            log.warn("event=invrollback_teleport_dispatch_failed player={}", live.getName());
        }
    }

    private void notify(PlayerRef staff, MessageKey key, Map<String, String> placeholders) {
        messageSink.deliver(staff, messages.resolve(staff, key, placeholders));
    }
}

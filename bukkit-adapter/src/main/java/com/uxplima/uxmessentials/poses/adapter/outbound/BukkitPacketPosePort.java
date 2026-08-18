package com.uxplima.uxmessentials.poses.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.poses.application.port.PosePort;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import com.uxplima.uxmlib.packet.npc.NpcPose;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PosePort} over uxmLib's packet layer — the same {@link NpcPackets} metadata seam the {@code npc} module
 * uses to freeze a fake player in a body pose, aimed here at a <em>real</em> player. A {@code /lay} or {@code
 * /bellyflop} overrides the player's {@code DATA_POSE} to {@link NpcPose#SWIMMING} so their body lies flat; a
 * {@code /spin} instead rotates the invisible seat the player rides (a real entity, so vanilla tracking carries the
 * rotation to viewers on its own) on a repeating scheduler pass.
 *
 * <p>Because the server never itself sends a swimming pose for a seated player, the lie-down is a client override:
 * the metadata packet is broadcast to everyone in the poser's world on the global region thread, where {@code
 * getOnlinePlayers()} is coherent under Folia, the {@code npc} renderer's discipline. It is sent to the poser too,
 * so they see their own pose in third person. The server re-syncs the real (standing) metadata on a mount, a new
 * nearby viewer, or a damage tick, so {@link #tick} re-asserts every held pose each pass rather than sending it
 * once and losing it. A crawl never reaches this port at all: it rides a real server-side swimming pose, which the
 * server syncs itself.
 * {@link #clearPose} resends the {@link NpcPose#STANDING} default to undo it and drops the player from the spin loop;
 * a player who never held a free pose is a clean no-op.
 *
 * <p>One repeating {@code repeatGlobal} pass drives both jobs (the {@code StaffFollowService} idiom): it re-asserts
 * each held lie-down/crawl pose on the global thread it fires on, then hops each spinning player onto their own entity
 * thread, where the seat rotation actually runs, so no entity is ever touched off its owning region. {@link
 * #shutdown()} cancels that pass on module stop.
 */
@NullMarked
public final class BukkitPacketPosePort implements PosePort {

    private final Server server;
    private final Scheduler scheduler;
    private final NpcPackets packets;
    private final Logger log;
    private final float spinStepDegrees;
    // The free poses in flight: LAY/BELLYFLOP need a STANDING reset on clear, SPIN needs the seat rotated each pass.
    private final ConcurrentHashMap<UUID, PoseType> active = new ConcurrentHashMap<>();
    // The running spin accumulator per player (degrees), advanced on the player's own entity thread each pass.
    private final ConcurrentHashMap<UUID, Float> spinning = new ConcurrentHashMap<>();
    private final AutoCloseable spinTask;

    public BukkitPacketPosePort(
            Server server,
            Scheduler scheduler,
            NpcPackets packets,
            Logger log,
            int spinIntervalTicks,
            float spinStepDegrees) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.packets = Objects.requireNonNull(packets, "packets");
        this.log = Objects.requireNonNull(log, "log");
        this.spinStepDegrees = spinStepDegrees;
        Duration period = Duration.ofMillis(Math.max(1L, spinIntervalTicks) * 50L);
        this.spinTask = scheduler.repeatGlobal(this::tick, period, period);
    }

    @Override
    public void applyPose(PlayerRef who, PoseType pose) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(pose, "pose");
        UUID id = who.uuid();
        active.put(id, pose);
        switch (pose) {
            // A lie-down is seen third-person by the poser too, so it goes to everyone.
            case LAY, BELLYFLOP -> broadcastPose(id, NpcPose.SWIMMING, true);
            case SPIN -> spinning.put(id, 0f);
            default ->
                throw new IllegalArgumentException("BukkitPacketPosePort renders only the free poses, not " + pose);
        }
    }

    @Override
    public void clearPose(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        UUID id = who.uuid();
        PoseType previous = active.remove(id);
        spinning.remove(id);
        if (previous != null) {
            // Reset the client's body pose to standing. Harmless for a spin (it never overrode the pose) and a
            // no-op when the player has already left — the send resolves nothing for an offline uuid.
            broadcastPose(id, NpcPose.STANDING, true);
        }
    }

    /** Whether {@code id} is currently spinning — the seat-rotation loop is advancing their yaw. */
    public boolean isSpinning(UUID id) {
        return spinning.containsKey(Objects.requireNonNull(id, "id"));
    }

    /**
     * One loop pass: re-assert every held lie-down pose so the server's own metadata re-sync (a mount, a new nearby
     * viewer, a damage tick) cannot clobber it back to standing, then advance each spin on its player's entity thread.
     * This runs on the global region thread the repeating loop fires on, where {@code getOnlinePlayers()} is coherent
     * under Folia, so the pose re-send needs no further hop.
     */
    public void tick() {
        active.forEach(this::refreshPose);
        for (UUID id : spinning.keySet()) {
            scheduler.onEntity(new PlayerRef(id, id.toString()), () -> rotate(id));
        }
    }

    // Re-send the body pose a lie-down overrides, so it holds against the server's re-sync. A spin overrides no pose
    // (it rotates the seat), and a sit never reaches this port, so both fall through to nothing.
    private void refreshPose(UUID id, PoseType pose) {
        switch (pose) {
            case LAY, BELLYFLOP -> sendPose(id, NpcPose.SWIMMING, true);
            default -> {
                // SPIN is advanced by the spin loop below; SIT/PLAYER_SIT/CRAWL never reach this port.
            }
        }
    }

    /** Cancel the spin pass and forget every pose, so a disable or reload leaves no running rotation. */
    public void shutdown() {
        try {
            spinTask.close();
        } catch (Exception e) {
            // A cancel failure must not strand the caller (the wiring disables the module after this); log and
            // carry on so the pose maps are still cleared and the disable completes.
            log.error("failed to cancel the poses spin task", e);
        }
        active.clear();
        spinning.clear();
    }

    // Advance the spin accumulator and turn the seat to it, on the player's own entity thread. computeIfPresent
    // never re-adds a player cleared between the pass dispatch and this hop, so a just-stopped spin stays stopped.
    private void rotate(UUID id) {
        Player player = server.getPlayer(id);
        if (player == null) {
            return;
        }
        Entity seat = player.getVehicle();
        if (seat == null || !seat.isValid()) {
            return;
        }
        Float next = spinning.computeIfPresent(id, (key, yaw) -> yaw + spinStepDegrees);
        if (next == null) {
            return;
        }
        float pitch =
                Objects.requireNonNull(seat.getLocation(), "seat location").getPitch();
        seat.setRotation(next, pitch);
    }

    private void broadcastPose(UUID id, NpcPose pose, boolean includeSelf) {
        scheduler.onGlobal(() -> sendPose(id, pose, includeSelf));
    }

    // Send the pose-metadata override to everyone sharing the poser's world. When includeSelf is false the poser's own
    // client is skipped; a lie-down includes them so they see their own pose in third person. Runs on the global
    // region thread, where getOnlinePlayers() is coherent
    // under Folia; a client not tracking the poser's entity id simply ignores the packet, so no range check is needed.
    private void sendPose(UUID id, NpcPose pose, boolean includeSelf) {
        Player posed = server.getPlayer(id);
        if (posed == null) {
            return;
        }
        Object packet = packets.pose(posed.getEntityId(), pose);
        for (Player viewer : server.getOnlinePlayers()) {
            if (!includeSelf && viewer.getUniqueId().equals(id)) {
                continue;
            }
            if (viewer.getWorld().equals(posed.getWorld())) {
                packets.send(viewer, packet);
            }
        }
    }
}

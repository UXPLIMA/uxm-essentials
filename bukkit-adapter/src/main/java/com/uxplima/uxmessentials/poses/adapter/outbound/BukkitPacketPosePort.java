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
 * uses to freeze a fake player in a body pose, aimed here at a <em>real</em> player. A {@code /lay} or
 * {@code /bellyflop} overrides the player's {@code DATA_POSE} to {@link NpcPose#SWIMMING} so their body lies flat;
 * a {@code /spin} instead rotates the invisible seat the player rides (a real entity, so vanilla tracking carries
 * the rotation to viewers on its own) on a repeating scheduler pass.
 *
 * <p>Because the server never itself sends a swimming pose for a seated player, the lie-down is a client override:
 * the metadata packet is broadcast to everyone in the poser's world (the poser included, so they see their own pose
 * in third person) on the global region thread, where {@code getOnlinePlayers()} is coherent under Folia — the
 * {@code npc} renderer's discipline. {@link #clearPose} resends the {@link NpcPose#STANDING} default to undo it and
 * drops the player from the spin loop; a player who never held a free pose is a clean no-op.
 *
 * <p>The spin rides one repeating {@code repeatGlobal} pass (the {@code StaffFollowService} idiom): the pass only
 * iterates the spinning set and hops each player onto their own entity thread, where the seat rotation actually
 * runs, so no entity is ever touched off its owning region. {@link #shutdown()} cancels that pass on module stop.
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
            case LAY, BELLYFLOP -> broadcastPose(id, NpcPose.SWIMMING);
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
            broadcastPose(id, NpcPose.STANDING);
        }
    }

    /** Whether {@code id} is currently spinning — the seat-rotation loop is advancing their yaw. */
    public boolean isSpinning(UUID id) {
        return spinning.containsKey(Objects.requireNonNull(id, "id"));
    }

    /** One spin pass: hop each spinning player onto their own entity thread and rotate their seat there. */
    public void tick() {
        for (UUID id : spinning.keySet()) {
            scheduler.onEntity(new PlayerRef(id, id.toString()), () -> rotate(id));
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

    private void broadcastPose(UUID id, NpcPose pose) {
        scheduler.onGlobal(() -> sendPose(id, pose));
    }

    // Send the pose-metadata override to everyone sharing the poser's world (the poser too, for their own third-
    // person view). Runs on the global region thread, where getOnlinePlayers() is coherent under Folia; a client
    // not tracking the poser's entity id simply ignores the packet, so no per-viewer range check is needed.
    private void sendPose(UUID id, NpcPose pose) {
        Player posed = server.getPlayer(id);
        if (posed == null) {
            return;
        }
        Object packet = packets.pose(posed.getEntityId(), pose);
        for (Player viewer : server.getOnlinePlayers()) {
            if (viewer.getWorld().equals(posed.getWorld())) {
                packets.send(viewer, packet);
            }
        }
    }
}

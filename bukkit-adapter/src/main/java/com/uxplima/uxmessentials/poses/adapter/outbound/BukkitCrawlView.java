package com.uxplima.uxmessentials.poses.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

import com.uxplima.uxmessentials.poses.application.port.CrawlView;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link CrawlView} over Paper's own swimming pose plus a client-only shulker used as a ceiling.
 *
 * <p>The pose half is plain Bukkit: a <em>fixed</em> {@link Pose#SWIMMING} lays the player flat as a real
 * server-side pose, so every onlooker and the crawler's own camera see the same thing and the hitbox shrinks the
 * way vanilla swimming does. Fixed is what makes it stick: an unpinned pose is recomputed from the player's
 * surroundings every tick, and a crawler is not in water, so the next tick would stand them straight back up.
 *
 * <p>The ceiling half is the interesting one. What stops a crawling player from simply standing back up is a low
 * roof, and the roof must exist for the client's own movement prediction, not just the server's. A fake block sent
 * to that one client would do it, but a block is terrain: the third-person camera collides with it and pulls in,
 * and the light around it changes, so the player sees an invisible box over their head. A shulker is the way out.
 * It is the one mob a client treats as solid to walk into, so a shulker whose box sits half a block above the
 * crawler's feet is a ceiling for movement and nothing at all for the camera or the light engine. It is spawned
 * through packets to the crawler alone, so no other player, and no server-side entity list, ever knows about it.
 *
 * <p>The box is invisible, attached to {@code UP} (so its collision hangs over the position it was spawned at) and
 * fully closed ({@code peek = 0}). {@link #hold} doubles as the follow call: a crawler walks around, so each move
 * teleports the same box to the new spot rather than respawning it. {@link #release} removes it and clears the
 * pose; a player who was never crawling simply has no box to remove, which makes the release safe on every exit.
 *
 * <p>The box index is keyed by player uuid and mutated only from that player's own region thread (the command, the
 * move listener, and the exits all run there), so a plain {@link ConcurrentHashMap} of per-player records is the
 * whole ownership model. An offline player is a clean no-op on both calls.
 */
@NullMarked
public final class BukkitCrawlView implements CrawlView {

    /** The mob the ceiling is made of: the one type a client collides with but never renders as terrain. */
    private static final String BOX_TYPE = "shulker";

    /**
     * How far above the crawler's feet the ceiling sits. A shulker's box is a block tall, so half a block of
     * clearance leaves the crawler their prone height and puts the roof exactly where a standing head would go.
     */
    private static final double BOX_HEIGHT = 0.5;

    private final Server server;
    private final NpcPackets packets;
    // The live ceiling per crawler. Written only from the crawler's own region thread; read on the same thread.
    private final ConcurrentHashMap<UUID, Box> boxes = new ConcurrentHashMap<>();

    public BukkitCrawlView(Server server, NpcPackets packets) {
        this.server = Objects.requireNonNull(server, "server");
        this.packets = Objects.requireNonNull(packets, "packets");
    }

    @Override
    public void hold(PlayerRef who, Position feet) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(feet, "feet");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        if (player.getPose() != Pose.SWIMMING) {
            // Fixed, so the server stops recomputing the pose from the player's surroundings: a crawler is not in
            // water, and without the pin the very next tick would put them back upright.
            player.setPose(Pose.SWIMMING, true);
        }
        Box box = boxes.computeIfAbsent(who.uuid(), id -> new Box(packets.allocateEntityId(), UUID.randomUUID()));
        double y = feet.y() + BOX_HEIGHT;
        if (box.spawned) {
            packets.send(player, packets.teleport(box.entityId, feet.x(), y, feet.z(), 0f, 0f));
            return;
        }
        packets.send(
                player,
                packets.bundle(List.of(
                        packets.spawnEntity(box.entityId, box.entityUuid, BOX_TYPE, feet.x(), y, feet.z(), 0f, 0f),
                        packets.sharedFlags(box.entityId, false, false, true),
                        packets.shulkerAttachFace(box.entityId, "up"),
                        packets.shulkerPeek(box.entityId, 0))));
        box.spawned = true;
    }

    @Override
    public void release(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Box box = boxes.remove(who.uuid());
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            // Offline: the client is gone, so both the pose and the box died with the connection.
            return;
        }
        // Unpin the pose and hand it back to the server, which recomputes it from the player's own state again.
        player.setPose(Pose.STANDING, false);
        if (box != null && box.spawned) {
            packets.send(player, packets.remove(box.entityId));
        }
    }

    /**
     * One crawler's ceiling: the fake entity id and uuid it was spawned under, and whether the spawn has been sent
     * yet. Owned by the crawler's own region thread, the only thread that reads or writes it.
     */
    private static final class Box {

        private final int entityId;
        private final UUID entityUuid;
        private boolean spawned;

        private Box(int entityId, UUID entityUuid) {
            this.entityId = entityId;
            this.entityUuid = entityUuid;
        }
    }

    /** Whether {@code who} currently has a ceiling above them, for the tests that pin the spawn-once behaviour. */
    public boolean isHolding(UUID who) {
        @Nullable Box box = boxes.get(Objects.requireNonNull(who, "who"));
        return box != null && box.spawned;
    }
}

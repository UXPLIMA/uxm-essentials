package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.uxplima.uxmessentials.homes.application.HomeRespawnLocator;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.ResolveRespawn;
import com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace;
import com.uxplima.uxmessentials.teleport.application.port.SafeLocationQueue;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.RespawnStep;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Drives the per-world {@link com.uxplima.uxmessentials.teleport.domain.RespawnChain} on death: walks the
 * configured chain for the world the player respawns into and, when a step resolves, redirects the
 * {@link PlayerRespawnEvent} to that position. A world that configures no chain resolves to nothing, so the
 * event is left untouched and the player respawns vanilla — the chain is strictly opt-in per world.
 *
 * <p>Wired step kinds:
 *
 * <ul>
 *   <li>{@code SPAWN} — the teleport spawn directory's resolved spawn for the respawn world.
 *   <li>{@code HOME} — the player's lowest-slot home through the homes {@link HomeRespawnLocator} seam (a
 *       non-blocking cache read; empty on a cold cache or no homes).
 *   <li>{@code BED} — the player's stored bed/anchor respawn position ({@link Player#getRespawnLocation()}),
 *       or empty when none is set.
 * </ul>
 *
 * <p>The remaining chain kinds — {@code ANCHOR}, {@code WARP}, {@code RANDOM} — return empty here (the chain
 * falls through to the next step). {@code BED} already covers the common anchor case, and the warp directory
 * is not handed to this listener. Respawn RTP is instead driven by the {@code rtp-on-respawn} config toggle
 * (a set of world names): when the chain resolves nothing for such a world, the listener serves a location
 * from the pre-warmed pool ({@link SafeLocationQueue#poll}) — an O(1) serve, never a synchronous search — and
 * applies the arrival grace, so a random respawn is immediate and never generates a chunk on the death screen.
 *
 * <p>The listener runs at {@link EventPriority#NORMAL} and only ever <em>sets</em> a location when the chain
 * or the RTP toggle resolves; otherwise it leaves whatever bed/anchor location another plugin or the server
 * already chose. Because the default chain is empty and {@code rtp-on-respawn} defaults to no worlds,
 * installing this listener changes nothing until an operator opts a world in.
 */
@NullMarked
public final class RespawnListener implements Listener {

    private final ResolveRespawn resolveRespawn;
    private final SpawnDirectory spawns;
    private final HomeRespawnLocator homeLocator;
    private final Server server;
    private final SafeLocationQueue rtpQueue;
    private final ArrivalGrace grace;
    private final Supplier<Set<String>> rtpOnRespawnWorlds;

    public RespawnListener(
            ResolveRespawn resolveRespawn,
            SpawnDirectory spawns,
            HomeRespawnLocator homeLocator,
            Server server,
            SafeLocationQueue rtpQueue,
            ArrivalGrace grace,
            Supplier<Set<String>> rtpOnRespawnWorlds) {
        this.resolveRespawn = Objects.requireNonNull(resolveRespawn, "resolveRespawn");
        this.spawns = Objects.requireNonNull(spawns, "spawns");
        this.homeLocator = Objects.requireNonNull(homeLocator, "homeLocator");
        this.server = Objects.requireNonNull(server, "server");
        this.rtpQueue = Objects.requireNonNull(rtpQueue, "rtpQueue");
        this.grace = Objects.requireNonNull(grace, "grace");
        this.rtpOnRespawnWorlds = Objects.requireNonNull(rtpOnRespawnWorlds, "rtpOnRespawnWorlds");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        WorldRef respawnWorld =
                BukkitRefs.toPosition(event.getRespawnLocation()).world();
        Optional<Position> resolved =
                resolveRespawn.resolve(respawnWorld, (world, step) -> resolveStep(player, world, step));
        if (resolved.isPresent()) {
            resolved.flatMap(this::toLocation).ifPresent(event::setRespawnLocation);
            return;
        }
        if (rtpOnRespawnWorlds.get().contains(respawnWorld.name())) {
            serveRtpRespawn(player, respawnWorld, event);
        }
    }

    private void serveRtpRespawn(Player player, WorldRef respawnWorld, PlayerRespawnEvent event) {
        Optional<RtpSafeLocation> location = rtpQueue.poll(respawnWorld);
        rtpQueue.requestRefill(respawnWorld);
        // A drained pool leaves the vanilla respawn location and warms for next time — never a blocking search.
        location.flatMap(l -> toLocation(l.position())).ifPresent(at -> {
            event.setRespawnLocation(at);
            grace.applyOnArrival(BukkitRefs.toRef(player));
        });
    }

    private Optional<Position> resolveStep(Player player, WorldRef respawnWorld, RespawnStep step) {
        return switch (step.kind()) {
            case SPAWN -> spawns.defaultSpawn(respawnWorld);
            case HOME -> homeLocator.respawnHome(BukkitRefs.toRef(player));
            case BED -> bedSpawn(player);
            // ANCHOR/WARP/RANDOM are not wired into the respawn listener yet (see the class Javadoc); the
            // chain falls through to the next step rather than blocking on a missing port.
            case ANCHOR, WARP, RANDOM -> Optional.empty();
        };
    }

    private static Optional<Position> bedSpawn(Player player) {
        Location respawn = player.getRespawnLocation();
        return respawn == null ? Optional.empty() : Optional.of(BukkitRefs.toPosition(respawn));
    }

    private Optional<Location> toLocation(Position position) {
        @Nullable World world = server.getWorld(position.world().uid());
        return Optional.ofNullable(world).map(w -> BukkitRefs.toLocation(w, position));
    }
}

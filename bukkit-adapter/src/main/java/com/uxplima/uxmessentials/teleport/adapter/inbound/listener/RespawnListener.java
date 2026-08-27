package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
import com.uxplima.uxmessentials.teleport.application.port.WarpRespawnLocator;
import com.uxplima.uxmessentials.teleport.domain.RespawnStep;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Drives the per-world {@link com.uxplima.uxmessentials.teleport.domain.RespawnChain} on death: walks the
 * configured chain for the world the player died in and, when a step resolves, redirects the
 * {@link PlayerRespawnEvent} to that position. The shipped default preserves a valid bed/anchor and otherwise
 * uses the resolved server spawn; the master switch or an explicit empty world override restores vanilla behavior.
 *
 * <p>Wired step kinds:
 *
 * <ul>
 *   <li>{@code SPAWN}: the same mirror → world spawn → main spawn → vanilla chain as {@code /spawn}.
 *   <li>{@code HOME}: the player's lowest-slot home through the homes {@link HomeRespawnLocator} seam (a
 *       non-blocking cache read; empty on a cold cache or no homes).
 *   <li>{@code BED}/{@code ANCHOR}: the vanilla landing, distinguished by Paper's respawn flags.
 *   <li>{@code WARP}: a named warp from the warps module's warmed snapshot.
 *   <li>{@code RANDOM}: an immediate O(1) serve from the pre-warmed RTP pool.
 * </ul>
 *
 * <p>The legacy {@code rtp-on-respawn} config toggle remains supported
 * (a set of world names): when the chain resolves nothing for such a world, the listener serves a location
 * from the pre-warmed pool ({@link SafeLocationQueue#poll}): an O(1) serve, never a synchronous search, and
 * applies the arrival grace, so a random respawn is immediate and never generates a chunk on the death screen.
 *
 * <p>The listener runs at {@link EventPriority#NORMAL} and only ever <em>sets</em> a location when the chain
 * or the RTP toggle resolves; otherwise it leaves whatever bed/anchor location another plugin or the server
 * already chose.
 */
@NullMarked
public final class RespawnListener implements Listener {

    private final ResolveRespawn resolveRespawn;
    private final Function<WorldRef, Optional<Position>> resolveSpawn;
    private final HomeRespawnLocator homeLocator;
    private final WarpRespawnLocator warpLocator;
    private final Server server;
    private final SafeLocationQueue rtpQueue;
    private final ArrivalGrace grace;
    private final Supplier<Set<String>> rtpOnRespawnWorlds;

    public RespawnListener(
            ResolveRespawn resolveRespawn,
            Function<WorldRef, Optional<Position>> resolveSpawn,
            HomeRespawnLocator homeLocator,
            WarpRespawnLocator warpLocator,
            Server server,
            SafeLocationQueue rtpQueue,
            ArrivalGrace grace,
            Supplier<Set<String>> rtpOnRespawnWorlds) {
        this.resolveRespawn = Objects.requireNonNull(resolveRespawn, "resolveRespawn");
        this.resolveSpawn = Objects.requireNonNull(resolveSpawn, "resolveSpawn");
        this.homeLocator = Objects.requireNonNull(homeLocator, "homeLocator");
        this.warpLocator = Objects.requireNonNull(warpLocator, "warpLocator");
        this.server = Objects.requireNonNull(server, "server");
        this.rtpQueue = Objects.requireNonNull(rtpQueue, "rtpQueue");
        this.grace = Objects.requireNonNull(grace, "grace");
        this.rtpOnRespawnWorlds = Objects.requireNonNull(rtpOnRespawnWorlds, "rtpOnRespawnWorlds");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        WorldRef deathWorld = BukkitRefs.toRef(player.getWorld());
        Optional<Position> resolved =
                resolveRespawn.resolve(deathWorld, (world, step) -> resolveStep(player, event, world, step));
        if (resolved.isPresent()) {
            resolved.flatMap(this::toLocation).ifPresent(event::setRespawnLocation);
            return;
        }
        if (rtpOnRespawnWorlds.get().contains(deathWorld.name())) {
            serveRtpRespawn(player, deathWorld, event);
        }
    }

    private void serveRtpRespawn(Player player, WorldRef respawnWorld, PlayerRespawnEvent event) {
        Optional<RtpSafeLocation> location = rtpQueue.poll(respawnWorld);
        rtpQueue.requestRefill(respawnWorld);
        // A drained pool leaves the vanilla respawn location and warms for next time, never a blocking search.
        location.flatMap(l -> toLocation(l.position())).ifPresent(at -> {
            event.setRespawnLocation(at);
            grace.applyOnArrival(BukkitRefs.toRef(player));
        });
    }

    private Optional<Position> resolveStep(
            Player player, PlayerRespawnEvent event, WorldRef deathWorld, RespawnStep step) {
        return switch (step.kind()) {
            case SPAWN -> resolveSpawn.apply(deathWorld);
            case HOME -> homeLocator.respawnHome(BukkitRefs.toRef(player));
            case BED -> vanillaRespawn(event.isBedSpawn(), event);
            case ANCHOR -> vanillaRespawn(event.isAnchorSpawn(), event);
            case WARP -> step.argumentValue().flatMap(warpLocator::respawnWarp);
            case RANDOM -> randomRespawn(player, deathWorld);
        };
    }

    private Optional<Position> randomRespawn(Player player, WorldRef world) {
        Optional<RtpSafeLocation> location = rtpQueue.poll(world);
        rtpQueue.requestRefill(world);
        if (location.isPresent()) {
            grace.applyOnArrival(BukkitRefs.toRef(player));
        }
        return location.map(RtpSafeLocation::position);
    }

    private static Optional<Position> vanillaRespawn(boolean matchingKind, PlayerRespawnEvent event) {
        return matchingKind ? Optional.of(BukkitRefs.toPosition(event.getRespawnLocation())) : Optional.empty();
    }

    private Optional<Location> toLocation(Position position) {
        @Nullable World world = server.getWorld(position.world().uid());
        return Optional.ofNullable(world).map(w -> BukkitRefs.toLocation(w, position));
    }
}

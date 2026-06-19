package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

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
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.RespawnStep;
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
 * <p>The remaining kinds — {@code ANCHOR}, {@code WARP}, {@code RANDOM} — return empty for now (the chain
 * falls through to the next step). They are deliberately not wired here: {@code BED} already covers the
 * common anchor case via the player's stored respawn location, the warp directory is not handed to this
 * listener, and the urgent RTP path must not block inside the respawn event. A later iteration can wire them
 * once those ports are passed in.
 *
 * <p>The listener runs at {@link EventPriority#NORMAL} and only ever <em>sets</em> a location when the chain
 * resolves; an empty chain or an unresolved chain leaves whatever bed/anchor location another plugin or the
 * server already chose. Because the default chain is empty, installing this listener changes nothing until an
 * operator opts a world in.
 */
@NullMarked
public final class RespawnListener implements Listener {

    private final ResolveRespawn resolveRespawn;
    private final SpawnDirectory spawns;
    private final HomeRespawnLocator homeLocator;
    private final Server server;

    public RespawnListener(
            ResolveRespawn resolveRespawn, SpawnDirectory spawns, HomeRespawnLocator homeLocator, Server server) {
        this.resolveRespawn = Objects.requireNonNull(resolveRespawn, "resolveRespawn");
        this.spawns = Objects.requireNonNull(spawns, "spawns");
        this.homeLocator = Objects.requireNonNull(homeLocator, "homeLocator");
        this.server = Objects.requireNonNull(server, "server");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        WorldRef respawnWorld =
                BukkitRefs.toPosition(event.getRespawnLocation()).world();
        Optional<Position> resolved =
                resolveRespawn.resolve(respawnWorld, (world, step) -> resolveStep(player, world, step));
        resolved.flatMap(this::toLocation).ifPresent(event::setRespawnLocation);
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

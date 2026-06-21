package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.application.port.NearbyPlayers;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NearbyPlayers} implementation for {@code /near}: the players within a radius of the viewer in the
 * same world, ordered nearest-first. It reads the viewer's live location on the viewer's region thread (the
 * thread the synchronous {@code within} call runs on), then reads every candidate's live position.
 *
 * <p>Scanning every other player's {@link Player#getLocation()} from the viewer's region thread is a torn read on
 * Folia — each player's position is owned by that player's own region. The candidate positions are therefore read
 * on the global region thread (where the whole roster is consistently readable) and snapshotted to plain
 * {@link Location}s before the distance maths runs; {@code /near} is a low-frequency info command, so the brief
 * global hop is acceptable. The {@code within} contract is synchronous, so the read is marshalled with a bounded
 * wait — and runs inline when the caller already owns the global thread, since scheduling onto the owning thread
 * and then blocking on it would deadlock. The radius filter and nearest-first sort are unchanged.
 */
@NullMarked
public final class BukkitNearbyPlayers implements NearbyPlayers {

    private static final Duration MARSHAL_TIMEOUT = Duration.ofSeconds(5);

    private final Scheduler scheduler;
    private final Logger log;

    public BukkitNearbyPlayers(Scheduler scheduler, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public List<Nearby> within(PlayerRef viewer, int radius) {
        Objects.requireNonNull(viewer, "viewer");
        Player self = Bukkit.getPlayer(viewer.uuid());
        if (self == null || !self.isOnline()) {
            return List.of();
        }
        World world = self.getWorld();
        // Paper marks Player#getLocation() nullable (null only for an entity with no world, which a
        // connected player always has); guard it at the boundary.
        Location origin = Objects.requireNonNull(self.getLocation(), "viewer location");
        double radiusSquared = (double) radius * radius;
        List<Located> candidates = snapshotPositions(viewer.uuid(), world.getUID());
        return candidates.stream()
                .flatMap(candidate -> measure(origin, candidate).stream())
                .filter(measured -> measured.squared() <= radiusSquared)
                .sorted(Comparator.comparingDouble(Measured::squared))
                .map(Measured::toNearby)
                .toList();
    }

    /**
     * Snapshot every candidate's ref and live location on the global region thread, excluding the viewer and any
     * player outside {@code worldId}. Runs inline when the caller already owns the global thread; otherwise it
     * marshals the read onto the global thread and waits up to {@link #MARSHAL_TIMEOUT}, returning an empty list on
     * timeout rather than blocking the viewer's region thread indefinitely.
     */
    private List<Located> snapshotPositions(java.util.UUID viewerId, java.util.UUID worldId) {
        if (scheduler.onGlobalThread()) {
            return readPositions(viewerId, worldId);
        }
        CompletableFuture<List<Located>> result = new CompletableFuture<>();
        scheduler.onGlobal(() -> result.complete(readPositions(viewerId, worldId)));
        try {
            return result.get(MARSHAL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("nearby-player scan timed out resolving roster positions on the global thread");
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("nearby-player scan failed resolving roster positions", e);
            return List.of();
        }
    }

    private static List<Located> readPositions(java.util.UUID viewerId, java.util.UUID worldId) {
        List<Located> located = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(viewerId)) {
                continue;
            }
            Location location = other.getLocation();
            if (location == null
                    || !worldId.equals(
                            location.getWorld() == null
                                    ? null
                                    : location.getWorld().getUID())) {
                continue;
            }
            located.add(new Located(BukkitRefs.toRef(other), location.clone()));
        }
        return located;
    }

    private static Optional<Measured> measure(Location origin, Located candidate) {
        if (!Objects.equals(candidate.location().getWorld(), origin.getWorld())) {
            return Optional.empty();
        }
        return Optional.of(new Measured(candidate.who(), candidate.location().distanceSquared(origin)));
    }

    /** A candidate's ref carried with a snapshot of its location, read once on the global thread. */
    private record Located(PlayerRef who, Location location) {}

    /** A nearby candidate carried with its squared distance so the filter and sort avoid repeated sqrt. */
    private record Measured(PlayerRef who, double squared) {

        Nearby toNearby() {
            return new Nearby(who, (int) Math.round(Math.sqrt(squared)));
        }
    }
}

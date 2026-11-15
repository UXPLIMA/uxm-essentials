package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.application.port.BackLocationStore;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.BackLocation;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.event.PlayerTeleported;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link TeleportExecutor} on Paper's {@code teleportAsync}. The hop is driven from the player's own
 * entity scheduler (so it is valid on Folia), the destination's live follow target is re-read at the
 * moment of the hop, the pre-teleport position is captured for {@code /back}, and {@link PlayerTeleported}
 * is published on arrival. Passengers and vehicles are retained; the post-teleport invulnerability grace
 * window is the engine's concern, not this adapter's.
 *
 * <p>The completion callback may resume on any region/IO thread, so the success message and the
 * {@code /back} capture hop back to the player's region thread, the one place the teleport flow
 * deliberately hops more than once.
 */
@NullMarked
public final class AsyncTeleportExecutor implements TeleportExecutor {

    private final Scheduler scheduler;
    private final BackLocationStore backStore;
    private final DomainEventPublisher events;
    private final Logger log;
    private final Clock clock;
    private final BooleanSupplier centerToggle;
    private final TeleportArrivalHud arrivalHud;
    private final TeleportArrivalEffects arrivalEffects;

    public AsyncTeleportExecutor(
            Scheduler scheduler,
            BackLocationStore backStore,
            DomainEventPublisher events,
            Logger log,
            Clock clock,
            BooleanSupplier centerToggle,
            TeleportArrivalHud arrivalHud,
            TeleportArrivalEffects arrivalEffects) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.backStore = Objects.requireNonNull(backStore, "backStore");
        this.events = Objects.requireNonNull(events, "events");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.centerToggle = Objects.requireNonNull(centerToggle, "centerToggle");
        this.arrivalHud = Objects.requireNonNull(arrivalHud, "arrivalHud");
        this.arrivalEffects = Objects.requireNonNull(arrivalEffects, "arrivalEffects");
    }

    @Override
    public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
        // A plain hop (admin /tp, positional) has no post-landing side effect.
        teleport(who, destination, kind, AsyncTeleportExecutor::noOnLanded);
    }

    private static void noOnLanded() {
        // Nothing hangs off a plain hop's arrival.
    }

    private static void noOnFailed() {
        // Nothing to undo when a hop that cost nothing misses.
    }

    @Override
    public void teleport(PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded) {
        teleport(who, destination, kind, onLanded, AsyncTeleportExecutor::noOnFailed);
    }

    @Override
    public void teleport(
            PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded, Runnable onFailed) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(onLanded, "onLanded");
        Objects.requireNonNull(onFailed, "onFailed");
        Arrival arrival = new Arrival(onLanded, onFailed);
        scheduler.onEntity(who, () -> hop(who, destination, kind, arrival, true, true), arrival::failed);
    }

    @Override
    public void relocate(PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(onLanded, "onLanded");
        scheduler.onEntity(
                who,
                () -> hop(
                        who,
                        destination,
                        kind,
                        new Arrival(onLanded, AsyncTeleportExecutor::noOnFailed),
                        false,
                        false));
    }

    /**
     * What runs once the hop resolves. Exactly one of the two callbacks runs, at most once: a paid hop refunds from
     * {@code onFailed}, so a miss reported twice, or a miss reported after a landing, would hand money out.
     */
    private static final class Arrival {

        private final Runnable onLanded;
        private final Runnable onFailed;
        private final AtomicBoolean settled = new AtomicBoolean();

        Arrival(Runnable onLanded, Runnable onFailed) {
            this.onLanded = onLanded;
            this.onFailed = onFailed;
        }

        void landed() {
            if (settled.compareAndSet(false, true)) {
                onLanded.run();
            }
        }

        void failed() {
            if (settled.compareAndSet(false, true)) {
                onFailed.run();
            }
        }
    }

    private void hop(
            PlayerRef who,
            Destination destination,
            TeleportKind kind,
            Arrival arrival,
            boolean captureBack,
            boolean showArrival) {
        try {
            dispatch(who, destination, kind, arrival, captureBack, showArrival);
        } catch (RuntimeException failure) {
            // Anything thrown before teleportAsync took over (the /back capture, the follow lookup, the call itself)
            // leaves the player where they stood, so it is a miss like any other.
            log.error("teleport of " + who.name() + " failed before the hop", failure);
            arrival.failed();
        }
    }

    private void dispatch(
            PlayerRef who,
            Destination destination,
            TeleportKind kind,
            Arrival arrival,
            boolean captureBack,
            boolean showArrival) {
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            arrival.failed(); // despawned between launch and hop: nothing to move
            return;
        }
        Position target = applyCentering(resolveTarget(destination));
        Location to = locationFor(target);
        if (to == null) {
            log.warn("teleport target world unloaded for {}", who.name());
            arrival.failed();
            return;
        }
        Position from = TeleportRefs.positionOf(player);
        if (captureBack) {
            captureBack(who, from);
        }
        // teleportAsync retains passengers and vehicles by default and performs the region hop and async
        // chunk load internally; the entity scheduler is already on the player's region thread.
        var ignored = player.teleportAsync(to)
                .whenComplete((ok, err) -> onArrival(who, kind, from, target, ok, err, arrival, showArrival));
    }

    private void onArrival(
            PlayerRef who,
            TeleportKind kind,
            Position from,
            Position to,
            Boolean ok,
            Throwable err,
            Arrival arrival,
            boolean showArrival) {
        if (err != null) {
            log.warn("teleport failed for {}: {}", who.name(), String.valueOf(err.getMessage()));
            arrival.failed();
            return;
        }
        if (!Boolean.TRUE.equals(ok)) {
            arrival.failed(); // Paper refused the hop (e.g. unloaded target), so the player stayed put
            return;
        }
        // The cooldown stamp and arrival grace hang off a real landing only, never a refusal. The player has moved
        // by now, so the landing is reported even when they leave in this tick or an arrival effect throws.
        LandedHop.report(
                scheduler,
                log,
                who,
                () -> {
                    events.publish(new PlayerTeleported(who, kind, from, to));
                    if (showArrival) {
                        arrivalHud.arrived(who, kind);
                        arrivalEffects.arrived(who, kind);
                    }
                },
                arrival::landed);
    }

    private Position applyCentering(Position target) {
        return centerToggle.getAsBoolean() ? target.atBlockCenter() : target;
    }

    private Position resolveTarget(Destination destination) {
        return destination
                .follow()
                .map(follow -> liveOrSeed(follow, destination.position()))
                .orElse(destination.position());
    }

    private Position liveOrSeed(PlayerRef follow, Position seed) {
        Player live = Bukkit.getPlayer(follow.uuid());
        return live != null && live.isOnline() ? TeleportRefs.positionOf(live) : seed;
    }

    private void captureBack(PlayerRef who, Position from) {
        backStore.capture(who, BackLocation.beforeTeleport(from, clock.instant()));
    }

    private static @org.jspecify.annotations.Nullable Location locationFor(Position position) {
        World world = Bukkit.getWorld(position.world().uid());
        return world == null ? null : BukkitRefs.toLocation(world, position);
    }
}

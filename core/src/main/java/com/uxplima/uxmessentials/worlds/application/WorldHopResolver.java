package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopNotice;
import com.uxplima.uxmessentials.shared.application.hop.HopNotices;
import com.uxplima.uxmessentials.shared.application.hop.HopResolution;
import com.uxplima.uxmessentials.shared.application.hop.HopResolver;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.worlds.application.port.SpawnSafety;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.application.port.WorldTeleporter;
import com.uxplima.uxmessentials.worlds.domain.AccessDecision;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldTeleportCause;
import com.uxplima.uxmessentials.worlds.domain.event.WorldEntryDenied;

/**
 * The world side of the shared cross-server hop. On the target the world is read again and landed in only while this
 * backend still runs a world of that name, at its live spawn, and only when that spawn passes the safety check. A
 * player's own visit also passes the world's access rules here, where the live player count is known; a staff send
 * skips them, as a local {@code /worlds tp} does. The entry fee goes back through the same {@link WorldEntryFee} the
 * origin charged on. A fee belongs to the server, so a world hop never carries a settlement.
 */
public final class WorldHopResolver implements HopResolver {

    /** Lets a player land on a world spawn the safety check refuses. */
    public static final String BYPASS_SAFETY = "uxmessentials.world.bypass.safety";

    private static final HopNotices NOTICES = new HopNotices(
            "world",
            Map.of(
                    HopNotice.SENDING, WorldsMessageKey.WORLD_CROSS_SERVER_SENDING,
                    HopNotice.ARRIVED, WorldsMessageKey.WORLD_CROSS_SERVER_ARRIVED,
                    HopNotice.TIMED_OUT, WorldsMessageKey.WORLD_CROSS_SERVER_TIMED_OUT,
                    HopNotice.MISROUTED, WorldsMessageKey.WORLD_CROSS_SERVER_MISROUTED,
                    HopNotice.FAILED, WorldsMessageKey.WORLD_CROSS_SERVER_FAILED,
                    HopNotice.UNAVAILABLE, WorldsMessageKey.WORLD_CROSS_SERVER_UNAVAILABLE,
                    HopNotice.REFUNDED, WorldsMessageKey.WORLD_CROSS_SERVER_REFUNDED));

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final WorldAccessPolicy policy;
    private final WorldTeleporter teleporter;
    private final WorldEntryFee entryFee;
    private final SpawnSafety safety;
    private final Permissions permissions;
    private final DomainEventPublisher events;
    private final Notifier notifier;
    private final Scheduler scheduler;

    public WorldHopResolver(
            WorldRepository repository,
            WorldEngine engine,
            WorldAccessPolicy policy,
            WorldTeleporter teleporter,
            WorldEntryFee entryFee,
            SpawnSafety safety,
            Permissions permissions,
            DomainEventPublisher events,
            Notifier notifier,
            Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.entryFee = Objects.requireNonNull(entryFee, "entryFee");
        this.safety = Objects.requireNonNull(safety, "safety");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.events = Objects.requireNonNull(events, "events");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public HopKind kind() {
        return HopKind.WORLD;
    }

    @Override
    public HopNotices notices() {
        return NOTICES;
    }

    @Override
    public String label(PendingHop hop) {
        return WorldHopTarget.decode(hop.target())
                .map(target -> target.world().value())
                .orElse(hop.target());
    }

    @Override
    public HopResolution resolve(PendingHop hop) {
        Optional<WorldHopTarget> target = WorldHopTarget.decode(hop.target());
        if (target.isEmpty()) {
            return HopResolution.unreachable(hop.target());
        }
        String label = target.get().world().value();
        // The repository answers only for this backend's worlds, so a world that moved or was removed since is gone.
        Optional<ManagedWorld> found = repository.find(target.get().world());
        if (found.isEmpty()) {
            return HopResolution.unreachable(label);
        }
        ManagedWorld world = found.get();
        boolean staff = target.get().staff();
        return HopResolution.landsAt(label, (who, onLanded, onMissed) -> land(who, world, staff, onLanded, onMissed));
    }

    /**
     * Load the world and read its live spawn on the global thread, judge the spawn on its own region thread, then move
     * the player on theirs. The hop is already claimed by now, so every way the move can fail to happen is a miss: a
     * world that will not load, a backend that is stopping, a player who left, or a failure on the way. The claimed
     * hop always ends in exactly one outcome.
     */
    private void land(PlayerRef who, ManagedWorld world, boolean staff, Runnable onLanded, Runnable onMissed) {
        try {
            scheduler.onGlobal(() -> landFromGlobal(who, world, staff, onLanded, onMissed), onMissed);
        } catch (RuntimeException unscheduled) {
            onMissed.run();
        }
    }

    private void landFromGlobal(
            PlayerRef who, ManagedWorld world, boolean staff, Runnable onLanded, Runnable onMissed) {
        try {
            if (!engine.isLoaded(world.name()) && engine.load(world).isErr()) {
                onMissed.run();
                return;
            }
            Optional<Position> spawn = engine.spawnPoint(world.name());
            if (spawn.isEmpty()) {
                onMissed.run();
                return;
            }
            Position at = spawn.get();
            scheduler.onRegion(at, () -> judgeThenArrive(who, world, at, staff, onLanded, onMissed), onMissed);
        } catch (RuntimeException failed) {
            onMissed.run();
        }
    }

    private void judgeThenArrive(
            PlayerRef who, ManagedWorld world, Position spawn, boolean staff, Runnable onLanded, Runnable onMissed) {
        boolean safe = isSafe(spawn);
        try {
            scheduler.onEntity(
                    who, () -> arriveIfAllowed(who, world, spawn, staff, safe, onLanded, onMissed), onMissed);
        } catch (RuntimeException unscheduled) {
            onMissed.run();
        }
    }

    private void arriveIfAllowed(
            PlayerRef who,
            ManagedWorld world,
            Position spawn,
            boolean staff,
            boolean safe,
            Runnable onLanded,
            Runnable onMissed) {
        if (!staff) {
            AccessDecision decision = policy.decide(who, world);
            if (!decision.allowed()) {
                events.publish(new WorldEntryDenied(world.name(), who, decision));
                notifier.send(
                        who,
                        WorldTeleportService.denialKey(decision),
                        WorldTeleportService.denialPlaceholders(decision, world));
                onMissed.run();
                return;
            }
        }
        if (!safe && !permissions.has(who, BYPASS_SAFETY)) {
            notifier.send(
                    who,
                    WorldsMessageKey.WORLD_CROSS_SERVER_UNSAFE,
                    Map.of("world", world.name().value()));
            onMissed.run();
            return;
        }
        teleporter.arrive(who, spawn, staff ? WorldTeleportCause.ADMIN : WorldTeleportCause.SPAWN, onLanded, onMissed);
    }

    private boolean isSafe(Position spawn) {
        try {
            return safety.isSafe(spawn);
        } catch (RuntimeException unreadable) {
            // A spawn that cannot be judged is treated as unsafe: the player is refunded, not dropped into it.
            return false;
        }
    }

    @Override
    public boolean refund(PlayerRef who, HopCharge charge) {
        return entryFee.refund(who, charge.amount(), charge.currencyId());
    }

    @Override
    public boolean settle(PlayerRef who, HopSettlement settlement, HopCharge charge) {
        // A world hop is sent without a settlement, so this is never asked; answering false hands the charge back
        // should a row ever carry one.
        return false;
    }
}

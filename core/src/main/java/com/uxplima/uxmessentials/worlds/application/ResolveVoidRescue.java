package com.uxplima.uxmessentials.worlds.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.worlds.application.port.RescueTargets;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.RescuePoint;
import com.uxplima.uxmessentials.worlds.domain.VoidRescueChain;
import com.uxplima.uxmessentials.worlds.domain.VoidRescueStep;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;

/**
 * The void-rescue use case: decide whether a world catches a player who falls out of it, and where that player
 * lands. The chain and the trigger height are per-world properties, so a lobby can send a faller back to spawn
 * while an event world hands them to a warp or to fixed coordinates.
 *
 * <p>Nothing here moves the player. The caller asks for a position and performs the hop, which keeps this class
 * free of the adapter and lets the listener decide whether it is cancelling a damage event or reacting to a
 * height.
 *
 * <p>A destination that is itself in the void would rescue a player straight back into the fall, so a burst of
 * {@value #MAX_RESCUES} rescues inside {@value #WINDOW_SECONDS} seconds disarms the rescue for that player: the
 * next fall is left to vanilla and the misconfiguration is logged once rather than looping in silence.
 */
public final class ResolveVoidRescue {

    static final int MAX_RESCUES = 3;
    static final int WINDOW_SECONDS = 10;

    private static final Duration WINDOW = Duration.ofSeconds(WINDOW_SECONDS);

    private final WorldRepository repository;
    private final RescueTargets targets;
    private final WorldLookup worlds;
    private final Logger log;
    private final Clock clock;

    /** Per-player rescue bursts. Owned entirely by this class and only ever mutated through {@code compute}. */
    private final ConcurrentHashMap<UUID, Burst> bursts = new ConcurrentHashMap<>();

    public ResolveVoidRescue(
            WorldRepository repository, RescueTargets targets, WorldLookup worlds, Logger log, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Whether {@code world} configures a rescue at all; false leaves every fall to vanilla. */
    public boolean armed(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return !chain(world).isEmpty();
    }

    /**
     * The height below which a fall is caught early, or empty when the world waits for the vanilla void damage.
     * Only worlds that are armed answer, so an unmanaged world never costs the move listener a lookup.
     */
    public OptionalInt triggerY(WorldRef world) {
        Objects.requireNonNull(world, "world");
        Optional<WorldSettings> settings = settings(world);
        if (settings.isEmpty()
                || settings.get().get(WorldProperties.VOID_RESCUE).isEmpty()) {
            return OptionalInt.empty();
        }
        return settings.get()
                .get(WorldProperties.VOID_RESCUE_Y)
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }

    /**
     * Where {@code who} should land after falling out of {@code world}, or empty when the world is unmanaged,
     * no step resolves, or this player has already burnt through the loop guard.
     */
    public Optional<Position> rescue(PlayerRef who, WorldRef world) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(world, "world");
        VoidRescueChain chain = chain(world);
        if (chain.isEmpty()) {
            return Optional.empty();
        }
        Optional<Position> resolved = chain.resolve(step -> resolveStep(world, step));
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        if (!allow(who, world)) {
            return Optional.empty();
        }
        return resolved;
    }

    /** Forget a player's burst, called when they leave so a rejoin starts from a clean window. */
    public void forget(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        bursts.remove(who.uuid());
    }

    private Optional<Position> resolveStep(WorldRef world, VoidRescueStep step) {
        return switch (step.kind()) {
            case SPAWN -> targets.spawn(world);
            case WARP -> step.warpName().flatMap(targets::warp);
            case AT -> step.rescuePoint().flatMap(this::located);
        };
    }

    private Optional<Position> located(RescuePoint point) {
        return worlds.findByName(point.world().value())
                .map(ref -> new Position(ref, point.x(), point.y(), point.z(), point.yaw(), point.pitch()));
    }

    private VoidRescueChain chain(WorldRef world) {
        return settings(world).map(s -> s.get(WorldProperties.VOID_RESCUE)).orElseGet(VoidRescueChain::none);
    }

    private Optional<WorldSettings> settings(WorldRef world) {
        WorldName name;
        try {
            name = WorldName.of(world.name());
        } catch (IllegalArgumentException unmanageableName) {
            return Optional.empty();
        }
        return repository.find(name).map(ManagedWorld::settings);
    }

    /** True while this player is inside their rescue budget; the call that exhausts it logs once. */
    private boolean allow(PlayerRef who, WorldRef world) {
        Instant now = clock.instant();
        Burst burst = bursts.compute(
                who.uuid(),
                (uuid, current) -> current == null || current.expired(now)
                        ? new Burst(1, now)
                        : new Burst(current.count() + 1, current.startedAt()));
        if (burst.count() <= MAX_RESCUES) {
            return true;
        }
        if (burst.count() == MAX_RESCUES + 1) {
            log.warn(
                    "void rescue for {} in world {} looped {} times in {}s and was disarmed;"
                            + " check that the void-rescue destination is not itself in the void",
                    who.name(),
                    world.name(),
                    MAX_RESCUES,
                    WINDOW_SECONDS);
        }
        return false;
    }

    /** One player's rescue burst: how many rescues have run since {@code startedAt}. */
    private record Burst(int count, Instant startedAt) {

        boolean expired(Instant now) {
            return !now.isBefore(startedAt.plus(WINDOW));
        }
    }
}

package com.uxplima.uxmessentials.worlds.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.application.port.WorldTeleporter;
import com.uxplima.uxmessentials.worlds.domain.AccessDecision;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldTeleportCause;
import com.uxplima.uxmessentials.worlds.domain.event.WorldEntryDenied;
import org.jspecify.annotations.NullMarked;

/**
 * The single entry point both for the self-service {@code /worlds spawn} (access-gated and fee-charged) and for
 * the staff {@code /worlds tp} override ({@link #forced}, which skips the gate and the fee).
 *
 * <p>{@link #spawn} runs the full pipeline: resolve the world (else {@link WorldError#NOT_FOUND}), load it on
 * demand, evaluate the {@link WorldAccessPolicy} gate (publishing {@link WorldEntryDenied} and refusing on a
 * denial), charge the {@link WorldProperties#ENTRY_FEE} unless the player holds {@link WorldAccessPolicy#BYPASS_NODE},
 * then hand the player off to the live spawn. The live spawn is authoritative. The stored custom spawn is pushed
 * onto the world when it loads, so the destination is always {@link WorldEngine#spawnPoint}. The fee is debited
 * only after the teleport is accepted, so a rejected hand-off never costs the player money.
 *
 * <p>A world this backend does not run is reached through {@link WorldHops} while the worlds {@code cross-server}
 * block is on: the access rules this backend can answer and the affordability check run on the caller's thread, then
 * the teleport rules, the charge and the hop run on the async lane, since the hop writes to the shared database. A
 * world of this backend always wins over a same-named world of another; a server named with the world picks one.
 */
@NullMarked
public final class WorldTeleportService {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final WorldAccessPolicy policy;
    private final WorldTeleporter teleporter;
    private final WorldEntryFee entryFee;
    private final Permissions permissions;
    private final DomainEventPublisher events;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final PlayerLookup players;
    private final WorldHops hops;

    public WorldTeleportService(
            WorldRepository repository,
            WorldEngine engine,
            WorldAccessPolicy policy,
            WorldTeleporter teleporter,
            WorldEntryFee entryFee,
            Permissions permissions,
            DomainEventPublisher events,
            Notifier notifier,
            Scheduler scheduler,
            PlayerLookup players,
            WorldHops hops) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.entryFee = Objects.requireNonNull(entryFee, "entryFee");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.events = Objects.requireNonNull(events, "events");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.players = Objects.requireNonNull(players, "players");
        this.hops = Objects.requireNonNull(hops, "hops");
    }

    /** Self-service entry to a world of this backend, or of the one backend that runs a world of that name. */
    public Result<Unit, WorldError> spawn(PlayerRef who, WorldName target) {
        return spawn(who, target, Optional.empty());
    }

    /**
     * Self-service entry: resolve, load, gate on access and fee, then hand the player off to the world spawn.
     * {@code server}, when named, picks the backend whose world of that name is meant.
     */
    public Result<Unit, WorldError> spawn(PlayerRef who, WorldName target, Optional<String> server) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(server, "server");
        Optional<ManagedWorld> found = asksHere(server) ? repository.find(target) : Optional.empty();
        if (found.isEmpty()) {
            return asksElsewhere(server) ? spawnRemote(who, target, server) : notFound(who, target);
        }
        ManagedWorld world = found.get();
        Optional<WorldError> loadFailure = ensureLoaded(world);
        if (loadFailure.isPresent()) {
            return fail(who, target, loadFailure.get(), loadFailure.get().messageKey());
        }
        Optional<WorldError> denied = gateAccess(who, world, policy.decide(who, world));
        if (denied.isPresent()) {
            return Result.err(denied.get());
        }
        BigDecimal fee = world.settings().get(WorldProperties.ENTRY_FEE);
        boolean charged = fee.signum() > 0 && !permissions.has(who, WorldAccessPolicy.BYPASS_NODE);
        if (charged && !entryFee.canAfford(who, fee)) {
            notify(who, WorldsMessageKey.WORLD_ENTER_FEE_INSUFFICIENT, feePlaceholders(target, fee));
            return Result.err(WorldError.ENTRY_FEE_UNAFFORDABLE);
        }
        return handOff(who, target, fee, charged);
    }

    /** Staff override to a world of this backend, or of the one backend that runs a world of that name. */
    public Result<Unit, WorldError> forced(PlayerRef actor, PlayerRef subject, WorldName target) {
        return forced(actor, subject, target, Optional.empty());
    }

    /**
     * Staff override to a world of this backend only, for a hand-off that must never leave it: a login redirect or the
     * evacuation of a world being restored.
     */
    public Result<Unit, WorldError> forcedHere(PlayerRef actor, PlayerRef subject, WorldName target) {
        return forced(actor, subject, target, Optional.of(hops.localServer()));
    }

    /**
     * Staff override: resolve, load, then teleport the subject to the world spawn ignoring restriction and fee.
     * {@code server}, when named, picks the backend whose world of that name is meant.
     */
    public Result<Unit, WorldError> forced(
            PlayerRef actor, PlayerRef subject, WorldName target, Optional<String> server) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(server, "server");
        Optional<ManagedWorld> found = asksHere(server) ? repository.find(target) : Optional.empty();
        if (found.isEmpty()) {
            if (!asksElsewhere(server)) {
                return notFound(actor, target);
            }
            Result<NetworkWorld, WorldError> remote = remoteWorld(actor, target, server);
            if (remote.isErr()) {
                return Result.err(remote.errorOrThrow());
            }
            NetworkWorld world = remote.orElseThrow();
            return dispatch(subject, world, () -> sendRemote(actor, subject, world, true, BigDecimal.ZERO, false));
        }
        Optional<WorldError> loadFailure = ensureLoaded(found.get());
        if (loadFailure.isPresent()) {
            return fail(actor, target, loadFailure.get(), loadFailure.get().messageKey());
        }
        Optional<Position> pos = engine.spawnPoint(target);
        if (pos.isEmpty()) {
            return fail(
                    actor, target, WorldError.DESTINATION_UNRESOLVED, WorldsMessageKey.WORLD_TP_DESTINATION_UNRESOLVED);
        }
        teleporter.teleport(subject, pos.get(), WorldTeleportCause.ADMIN);
        if (!actor.equals(subject)) {
            notify(actor, WorldsMessageKey.WORLD_TP_OTHER, Map.of("player", subject.name(), "world", target.value()));
        }
        return Result.ok();
    }

    private boolean asksHere(Optional<String> server) {
        return server.map(hops.localServer()::equals).orElse(true);
    }

    private boolean asksElsewhere(Optional<String> server) {
        return hops.enabled() && !server.map(hops.localServer()::equals).orElse(false);
    }

    /**
     * Self-service entry to a world another backend runs. What this backend can judge is judged here, before
     * anything is charged: the entry node and whether the player can afford the fee. The limit is the landing's.
     */
    private Result<Unit, WorldError> spawnRemote(PlayerRef who, WorldName target, Optional<String> server) {
        Result<NetworkWorld, WorldError> remote = remoteWorld(who, target, server);
        if (remote.isErr()) {
            return Result.err(remote.errorOrThrow());
        }
        NetworkWorld world = remote.orElseThrow();
        Optional<WorldError> denied = gateAccess(who, world.world(), policy.decideAhead(who, world.world()));
        if (denied.isPresent()) {
            return Result.err(denied.get());
        }
        BigDecimal fee = world.world().settings().get(WorldProperties.ENTRY_FEE);
        boolean charged = fee.signum() > 0 && !permissions.has(who, WorldAccessPolicy.BYPASS_NODE);
        if (charged && !entryFee.canAfford(who, fee)) {
            notify(who, WorldsMessageKey.WORLD_ENTER_FEE_INSUFFICIENT, feePlaceholders(target, fee));
            return Result.err(WorldError.ENTRY_FEE_UNAFFORDABLE);
        }
        return dispatch(who, world, () -> sendRemote(who, who, world, false, fee, charged));
    }

    /**
     * The one world of that name another backend runs, or the refusal the caller has been told: none, or several
     * with no server named to pick one. A world no backend has claimed has nowhere to be sent to.
     */
    private Result<NetworkWorld, WorldError> remoteWorld(PlayerRef asker, WorldName target, Optional<String> server) {
        String local = hops.localServer();
        List<NetworkWorld> matches = repository.network().stream()
                .filter(world -> world.name().equals(target))
                .filter(world -> world.knownServer().isPresent() && !world.livesOn(local))
                .filter(world -> server.map(world::livesOn).orElse(true))
                .toList();
        if (matches.isEmpty()) {
            notFound(asker, target);
            return Result.err(WorldError.NOT_FOUND);
        }
        if (matches.size() > 1) {
            String servers = matches.stream().map(NetworkWorld::server).sorted().collect(Collectors.joining(", "));
            notify(
                    asker,
                    WorldsMessageKey.WORLD_CROSS_SERVER_AMBIGUOUS,
                    Map.of("world", target.value(), "servers", servers));
            return Result.err(WorldError.AMBIGUOUS_SERVER);
        }
        return Result.ok(matches.get(0));
    }

    /**
     * Hand a hop to the async lane. Nothing has been charged yet, so a lane that refuses the task only has to say the
     * world cannot be reached.
     */
    private Result<Unit, WorldError> dispatch(PlayerRef subject, NetworkWorld world, Runnable send) {
        try {
            scheduler.async(send);
            return Result.ok();
        } catch (RuntimeException refused) {
            notify(subject, WorldsMessageKey.WORLD_CROSS_SERVER_UNAVAILABLE, hopPlaceholders(world));
            return Result.err(WorldError.UNREACHABLE);
        }
    }

    /**
     * Off-tick: the teleport rules, then the charge, then the hop that carries it. A player who left before the task
     * ran is neither charged nor sent: the connect could not reach them, and the charge would wait out the TTL.
     */
    private void sendRemote(
            PlayerRef actor, PlayerRef subject, NetworkWorld world, boolean byStaff, BigDecimal fee, boolean charge) {
        if (!players.isOnline(subject.uuid())) {
            return;
        }
        if (!hops.admit(subject, world, byStaff)) {
            return;
        }
        String name = world.name().value();
        Optional<HopCharge> charged = Optional.empty();
        if (charge) {
            if (!entryFee.charge(subject, fee)) {
                notify(subject, WorldsMessageKey.WORLD_ENTER_FEE_INSUFFICIENT, feePlaceholders(world.name(), fee));
                return;
            }
            charged = Optional.of(new HopCharge(fee, entryFee.currencyId()));
            notify(subject, WorldsMessageKey.WORLD_ENTER_FEE_CHARGED, feePlaceholders(world.name(), fee));
        }
        hops.send(subject, world, byStaff, charged);
        if (!actor.equals(subject)) {
            notify(actor, WorldsMessageKey.WORLD_TP_OTHER, Map.of("player", subject.name(), "world", name));
        }
    }

    private Result<Unit, WorldError> handOff(PlayerRef who, WorldName target, BigDecimal fee, boolean charged) {
        Optional<Position> pos = engine.spawnPoint(target);
        if (pos.isEmpty()) {
            return fail(
                    who, target, WorldError.DESTINATION_UNRESOLVED, WorldsMessageKey.WORLD_TP_DESTINATION_UNRESOLVED);
        }
        boolean accepted = teleporter.teleport(who, pos.get(), WorldTeleportCause.SPAWN);
        if (accepted && charged) {
            entryFee.charge(who, fee);
            notify(who, WorldsMessageKey.WORLD_ENTER_FEE_CHARGED, feePlaceholders(target, fee));
        }
        return Result.ok();
    }

    /** Load the world if the engine has not yet, surfacing the engine's error when the load fails. */
    private Optional<WorldError> ensureLoaded(ManagedWorld world) {
        if (engine.isLoaded(world.name())) {
            return Optional.empty();
        }
        Result<Unit, WorldError> result = engine.load(world);
        return result.isErr() ? Optional.of(result.errorOrThrow()) : Optional.empty();
    }

    /** Act on an access decision, publishing the denial event and notifying the precise reason key when refused. */
    private Optional<WorldError> gateAccess(PlayerRef who, ManagedWorld world, AccessDecision decision) {
        if (decision.allowed()) {
            return Optional.empty();
        }
        events.publish(new WorldEntryDenied(world.name(), who, decision));
        notify(who, denialKey(decision), denialPlaceholders(decision, world));
        return Optional.of(WorldError.ACCESS_DENIED);
    }

    static WorldsMessageKey denialKey(AccessDecision decision) {
        return switch (decision) {
            case DENIED_FULL -> WorldsMessageKey.WORLD_ENTER_DENIED_FULL;
            case DENIED_PERMISSION, ALLOWED -> WorldsMessageKey.WORLD_ENTER_DENIED_PERMISSION;
        };
    }

    static Map<String, String> denialPlaceholders(AccessDecision decision, ManagedWorld world) {
        String name = world.name().value();
        if (decision == AccessDecision.DENIED_FULL) {
            return Map.of(
                    "world", name, "limit", String.valueOf(world.settings().get(WorldProperties.PLAYER_LIMIT)));
        }
        return Map.of("world", name);
    }

    private static Map<String, String> feePlaceholders(WorldName target, BigDecimal fee) {
        return Map.of("world", target.value(), "amount", fee.toPlainString());
    }

    private static Map<String, String> hopPlaceholders(NetworkWorld world) {
        return Map.of("world", world.name().value(), "server", world.server());
    }

    private Result<Unit, WorldError> notFound(PlayerRef who, WorldName target) {
        return fail(who, target, WorldError.NOT_FOUND, WorldsMessageKey.WORLD_NOT_FOUND);
    }

    private Result<Unit, WorldError> fail(PlayerRef who, WorldName target, WorldError error, MessageKey key) {
        notify(who, key, Map.of("world", target.value()));
        return Result.err(error);
    }

    /** Folia-safe notify: bounce the delivery back onto the recipient's region thread. */
    private void notify(PlayerRef who, MessageKey key, Map<String, String> placeholders) {
        scheduler.onEntity(who, () -> notifier.send(who, key, placeholders));
    }
}

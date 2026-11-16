package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpLimit;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.domain.ReservedWarpNames;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpCreated;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpCreating;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldBlacklist;

/**
 * {@code /setpwarp <name>}: create a player-owned warp at the player's current position, or re-anchor an
 * existing one of the same name in place. Because warp names are now server-wide unique, a {@code /setpwarp}
 * onto a name that already exists is only a re-anchor when the caller owns that name. It keeps the warp's
 * access, status, and creation time and saves with the {@code moved} feedback. A name held by <em>another</em>
 * player is refused with {@link PlayerWarpError#NAME_TAKEN}: the whole point of global names is that they cannot
 * collide across owners. A brand-new name is gated against the owner's resolved {@link PlayerWarpLimit}, hitting
 * the cap returns {@link PlayerWarpError#LIMIT_REACHED}. Otherwise it is stored as a new private warp and
 * publishes {@code PlayerWarpCreated}.
 *
 * <p>Two limits gate a new warp, both resolved through {@link PlayerWarpQuota}: the owner's limit across every world,
 * against all their warps, and, when they hold a {@code uxmessentials.pwarp.limit.<world>.<n>} node for the new warp's
 * world, that world's limit against their warps in that world only. Re-anchoring a warp into another world asks the
 * world's limit too, so moving warps in cannot walk past it. {@code ownerName} is the player's display name,
 * captured onto the new warp so a browse can render its author without a lookup; the edit timestamp comes from
 * the injected {@link Clock}, never from the domain.
 *
 * <p>A new or re-anchored warp records {@code recordedServer}, the backend whose world it is in. It is empty off a
 * network or under the shipped default server name: a name every standalone backend shares would send the warp to a
 * server that does not exist once the backend joins a network under its own name. The warp then records no server,
 * and the backend holding its world claims it at its next start.
 */
public final class SetPlayerWarp {

    private final PlayerWarpRepository repository;
    private final PlayerWarpQuota quota;
    private final PlayerWarpWorldLimit worldLimit;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final DomainGate gate;
    private final Clock clock;
    private final WorldBlacklist worldBlacklist;
    private final Optional<String> recordedServer;

    /** The use case with nothing outside the plugin able to refuse a warp. The form the pure tests use. */
    public SetPlayerWarp(
            PlayerWarpRepository repository,
            PlayerWarpQuota quota,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock,
            WorldBlacklist worldBlacklist,
            Optional<String> recordedServer) {
        this(repository, quota, notifier, events, DomainGate.allowAll(), clock, worldBlacklist, recordedServer);
    }

    public SetPlayerWarp(
            PlayerWarpRepository repository,
            PlayerWarpQuota quota,
            Notifier notifier,
            DomainEventPublisher events,
            DomainGate gate,
            Clock clock,
            WorldBlacklist worldBlacklist,
            Optional<String> recordedServer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.quota = Objects.requireNonNull(quota, "quota");
        this.worldLimit = new PlayerWarpWorldLimit(quota, repository, notifier, worldBlacklist);
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.worldBlacklist = Objects.requireNonNull(worldBlacklist, "worldBlacklist");
        this.recordedServer = Objects.requireNonNull(recordedServer, "recordedServer");
    }

    /** Create {@code owner}'s warp {@code name} at {@code at}, or re-anchor it when the owner already holds it. */
    public Result<Unit, PlayerWarpError> set(PlayerRef owner, String ownerName, PlayerWarpName name, Position at) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");

        if (ReservedWarpNames.isReserved(name)) {
            notifier.send(owner, PlayerWarpError.RESERVED_NAME.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.RESERVED_NAME);
        }

        if (worldBlacklist.bars(at.world())) {
            notifier.send(
                    owner,
                    PlayerWarpError.WORLD_BLACKLISTED.messageKey(),
                    Map.of("world", at.world().name()));
            return Result.err(PlayerWarpError.WORLD_BLACKLISTED);
        }

        Optional<PlayerWarp> existing = repository.findByName(name);
        if (existing.isEmpty()) {
            return create(owner, ownerName, name, at);
        }
        return reanchorOrReject(owner, existing.get(), at);
    }

    private Result<Unit, PlayerWarpError> reanchorOrReject(PlayerRef owner, PlayerWarp existing, Position at) {
        if (!existing.owner().uuid().equals(owner.uuid())) {
            notifier.send(
                    owner,
                    PlayerWarpError.NAME_TAKEN.messageKey(),
                    Map.of("warp", existing.name().value()));
            return Result.err(PlayerWarpError.NAME_TAKEN);
        }
        Optional<PlayerWarpError> refused = worldLimit.refusesMove(owner, existing, at.world());
        if (refused.isPresent()) {
            return Result.err(refused.get());
        }
        repository.save(existing.movedTo(at, recordedServer, clock.instant()));
        notifier.send(
                owner,
                PlayerwarpsMessageKey.PWARP_MOVED,
                Map.of("warp", existing.name().value()));
        return Result.ok();
    }

    private Result<Unit, PlayerWarpError> create(PlayerRef owner, String ownerName, PlayerWarpName name, Position at) {
        PlayerWarpLimit limit = quota.resolve(owner);
        if (limit.isReachedAt(repository.count(owner))) {
            notifier.send(
                    owner, PlayerWarpError.LIMIT_REACHED.messageKey(), Map.of("limit", Integer.toString(limit.cap())));
            return Result.err(PlayerWarpError.LIMIT_REACHED);
        }
        if (!worldLimit.admitsNew(owner, owner, at.world())) {
            return Result.err(PlayerWarpError.LIMIT_REACHED);
        }
        // Past the owner's quota and with nothing written yet. Only the create is asked about: re-anchoring keeps
        // the warp that already exists, which whoever cared already saw created.
        if (!gate.allows(new PlayerWarpCreating(owner, name, at))) {
            notifier.send(owner, PlayerWarpError.VETOED.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.VETOED);
        }
        repository.save(
                PlayerWarp.create(owner, ownerName, name, at, clock.instant()).onServer(recordedServer));
        events.publish(new PlayerWarpCreated(owner, name, at));
        notifier.send(owner, PlayerwarpsMessageKey.PWARP_SET, Map.of("warp", name.value()));
        return Result.ok();
    }
}

package com.uxplima.uxmessentials.worlds.adapter.outbound.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmWorldsQuery;
import com.uxplima.uxmessentials.api.view.UxmWorld;
import com.uxplima.uxmessentials.api.view.UxmWorldAccess;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.worlds.application.WorldAccessPolicy;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.AccessDecision;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * The published worlds query, over the register the {@code /world} commands read and the same entry gate they
 * apply.
 *
 * <p>A world name that could never be a world name, because of a path separator or a stray colon, is not an error
 * to report: it simply names no world, so it reads as absent and as loaded-not. That keeps a consumer passing
 * through user input from having to guard the call.
 *
 * <p>The register is in the database, so listing waits on a read; whether a world is loaded is something the
 * server already knows, so that one answers straight away.
 */
@NullMarked
public final class WorldQueries implements UxmWorldsQuery {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final WorldAccessPolicy access;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public WorldQueries(
            WorldRepository repository,
            WorldEngine engine,
            WorldAccessPolicy access,
            PlayerLookup players,
            Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.access = Objects.requireNonNull(access, "access");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<List<UxmWorld>> list() {
        return AsyncQueries.supply(
                scheduler, () -> repository.all().stream().map(this::view).toList());
    }

    @Override
    public CompletableFuture<Optional<UxmWorld>> get(String name) {
        Objects.requireNonNull(name, "name");
        Optional<WorldName> parsed = parse(name);
        if (parsed.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return AsyncQueries.supply(
                scheduler, () -> repository.find(parsed.get()).map(this::view));
    }

    @Override
    public boolean isLoaded(String name) {
        Objects.requireNonNull(name, "name");
        return parse(name).map(engine::isLoaded).orElse(false);
    }

    @Override
    public CompletableFuture<UxmWorldAccess> access(UUID playerId, String worldName) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(worldName, "worldName");
        Optional<WorldName> parsed = parse(worldName);
        if (parsed.isEmpty()) {
            return CompletableFuture.completedFuture(UxmWorldAccess.ALLOWED);
        }
        return AsyncQueries.supply(
                scheduler,
                () -> repository
                        .find(parsed.get())
                        .map(world -> decision(access.decide(ApiValues.subject(players, playerId), world)))
                        .orElse(UxmWorldAccess.ALLOWED));
    }

    private static Optional<WorldName> parse(String name) {
        try {
            return Optional.of(WorldName.of(name));
        } catch (IllegalArgumentException notAWorldName) {
            return Optional.empty();
        }
    }

    private UxmWorld view(ManagedWorld world) {
        boolean loaded = engine.isLoaded(world.name());
        return new UxmWorld(
                world.name().value(),
                world.alias(),
                world.spec().environment().name(),
                world.spec().worldType().name(),
                world.spec().seed(),
                world.autoLoad(),
                loaded,
                loaded ? engine.playerCount(world.name()) : 0);
    }

    private static UxmWorldAccess decision(AccessDecision decision) {
        return switch (decision) {
            case ALLOWED -> UxmWorldAccess.ALLOWED;
            case DENIED_PERMISSION -> UxmWorldAccess.DENIED_PERMISSION;
            case DENIED_FULL -> UxmWorldAccess.DENIED_FULL;
        };
    }
}

package com.uxplima.uxmessentials.teleport.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmTeleportActions;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.application.CaptureBack;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.NullMarked;

/**
 * The published teleport actions, over the same executor {@code /tp} uses and the same return {@code /back} runs.
 *
 * <p>A plugin sending somebody somewhere wants the staff hop, not the player's: no warmup to stand still for, no
 * cooldown, no fee, nobody to accept it. What it does want is everything the executor brings, which is why this
 * does not reach for Bukkit itself: the region hop happens off the tick thread, passengers come along, the arrival
 * grace applies, the {@code /back} point is captured, and the teleport event fires.
 *
 * <p>The teleport's future completes on landing rather than on dispatch, through the executor's own arrival seam.
 * The return's completes once the return is accepted, because from there the player owns it: a warmup they can
 * walk out of is theirs to walk out of.
 */
@NullMarked
public final class TeleportActions implements UxmTeleportActions {

    private static final String DEATH_BACK_NODE = "uxmessentials.back.ondeath";

    private final TeleportExecutor executor;
    private final CaptureBack captureBack;
    private final TeleportSettings settings;
    private final PlayerLookup players;
    private final WorldLookup worlds;
    private final Permissions permissions;
    private final Scheduler scheduler;

    public TeleportActions(
            TeleportExecutor executor,
            CaptureBack captureBack,
            TeleportSettings settings,
            PlayerLookup players,
            WorldLookup worlds,
            Permissions permissions,
            Scheduler scheduler) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.captureBack = Objects.requireNonNull(captureBack, "captureBack");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.players = Objects.requireNonNull(players, "players");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> teleport(UUID playerId, UxmLocation location) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(location, "location");
        if (!players.isOnline(playerId)) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "there is nobody to move"));
        }
        Optional<Position> target = ApiValues.position(worlds, location);
        if (target.isEmpty()) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.NOT_FOUND, "no loaded world named " + location.world()));
        }
        PlayerRef who = ApiValues.subject(players, playerId);
        CompletableFuture<UxmOutcome> landed = new CompletableFuture<>();
        scheduler.onEntity(
                who,
                () -> executor.teleport(
                        who, Destination.at(target.get()), TeleportKind.ADMIN, () -> landed.complete(UxmOutcome.ok())),
                () -> landed.complete(
                        UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the player left before the hop could start")));
        return landed;
    }

    @Override
    public CompletableFuture<UxmOutcome> back(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!players.isOnline(playerId)) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "there is nobody to return"));
        }
        PlayerRef who = ApiValues.subject(players, playerId);
        CompletableFuture<UxmOutcome> answer = new CompletableFuture<>();
        scheduler.onEntity(
                who,
                () -> {
                    boolean deathAllowed = settings.backOnDeathEnabled() && permissions.has(who, DEATH_BACK_NODE);
                    Result<Unit, TeleportError> result =
                            captureBack.back(who, deathAllowed, settings.backDeathDelaySeconds());
                    answer.complete(
                            result.isErr() ? UxmOutcome.failed(failure(result.errorOrThrow())) : UxmOutcome.ok());
                },
                () -> answer.complete(
                        UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the player left before the return could start")));
        return answer;
    }

    /** Which published code a teleport refusal is. */
    private static UxmFailure failure(TeleportError error) {
        return switch (error) {
            case NO_BACK_LOCATION -> UxmFailure.of(UxmFailure.NOT_FOUND, "there is nowhere to go back to");
            case BACK_ON_DEATH_DENIED ->
                UxmFailure.of(UxmFailure.REFUSED, "returning to a death point is not allowed here");
            case BACK_ON_DEATH_DELAY -> UxmFailure.of(UxmFailure.REFUSED, "the post-death wait has not run out");
            case VETOED -> UxmFailure.of(UxmFailure.CANCELLED, "another plugin refused it");
            case TARGET_OFFLINE -> UxmFailure.of(UxmFailure.PLAYER_OFFLINE, "that player is not online");
            default ->
                UxmFailure.of(
                        UxmFailure.REFUSED,
                        "teleport refused it: " + error.name().toLowerCase(java.util.Locale.ROOT));
        };
    }
}

package com.uxplima.uxmessentials.moderation.adapter.outbound.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmModerationActions;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmResult;
import com.uxplima.uxmessentials.api.view.UxmSanction;
import com.uxplima.uxmessentials.api.view.UxmWarn;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerWarned;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiActors;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * The published moderation actions, over the same use cases the punishment commands run.
 *
 * <p>The actor is the calling plugin, so the ban a plugin lays down names that plugin wherever a staff-issued one
 * would name a staff member: the stored issuer, the history line, the operator audit. Nothing else about the write
 * changes, which is the point: a punishment is one thing on this server however it was asked for.
 *
 * <p>All of it runs on the async lane, like the commands: the use cases read and write the database there, and the
 * few things they do to the running server (disconnecting the player a ban just banned, announcing the punishment)
 * hop to the right thread themselves. The permission answers a sanction needs (is the target immune) are read on the
 * server thread on the way there. The caller gets a future that completes once the change is stored.
 */
@NullMarked
public final class ModerationActions implements UxmModerationActions {

    private final ModerationApiWrites writes;
    private final PlayerLookup players;
    private final Scheduler scheduler;
    private final Messages messages;
    private final String source;
    private final boolean silent;

    /** {@code messages} reads a sanction's reason the way {@link ModerationQueries} does. */
    public ModerationActions(
            ModerationApiWrites writes, PlayerLookup players, Scheduler scheduler, Messages messages, String source) {
        this(writes, players, scheduler, messages, source, false);
    }

    private ModerationActions(
            ModerationApiWrites writes,
            PlayerLookup players,
            Scheduler scheduler,
            Messages messages,
            String source,
            boolean silent) {
        this.writes = Objects.requireNonNull(writes, "writes");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.source = Objects.requireNonNull(source, "source");
        this.silent = silent;
    }

    @Override
    public UxmModerationActions silently() {
        return silent ? this : new ModerationActions(writes, players, scheduler, messages, source, true);
    }

    @Override
    public CompletableFuture<UxmResult<UxmSanction>> ban(UUID targetId, String reason) {
        return banning(targetId, Optional.of(Objects.requireNonNull(reason, "reason")));
    }

    @Override
    public CompletableFuture<UxmResult<UxmSanction>> ban(UUID targetId) {
        return banning(targetId, Optional.empty());
    }

    @Override
    public CompletableFuture<UxmResult<UxmSanction>> tempBan(UUID targetId, Duration duration, String reason) {
        return tempBanning(targetId, duration, Optional.of(Objects.requireNonNull(reason, "reason")));
    }

    @Override
    public CompletableFuture<UxmResult<UxmSanction>> tempBan(UUID targetId, Duration duration) {
        return tempBanning(targetId, duration, Optional.empty());
    }

    @Override
    public CompletableFuture<UxmOutcome> unban(UUID targetId) {
        PlayerRef target = subject(targetId);
        return offServer(target, () -> outcome(writes.unban().unban(actor(), target)));
    }

    @Override
    public CompletableFuture<UxmResult<UxmSanction>> mute(UUID targetId, String reason) {
        return muting(targetId, "", Optional.of(Objects.requireNonNull(reason, "reason")));
    }

    @Override
    public CompletableFuture<UxmResult<UxmSanction>> mute(UUID targetId) {
        return muting(targetId, "", Optional.empty());
    }

    @Override
    public CompletableFuture<UxmResult<UxmSanction>> tempMute(UUID targetId, Duration duration, String reason) {
        return muting(targetId, span(duration), Optional.of(Objects.requireNonNull(reason, "reason")));
    }

    @Override
    public CompletableFuture<UxmResult<UxmSanction>> tempMute(UUID targetId, Duration duration) {
        return muting(targetId, span(duration), Optional.empty());
    }

    @Override
    public CompletableFuture<UxmOutcome> unmute(UUID targetId) {
        PlayerRef target = subject(targetId);
        return offServer(target, () -> outcome(writes.unmute().unmute(actor(), target)));
    }

    @Override
    public CompletableFuture<UxmOutcome> kick(UUID targetId, String reason) {
        return kicking(targetId, Optional.of(Objects.requireNonNull(reason, "reason")));
    }

    @Override
    public CompletableFuture<UxmOutcome> kick(UUID targetId) {
        return kicking(targetId, Optional.empty());
    }

    @Override
    public CompletableFuture<UxmResult<UxmWarn>> warn(UUID targetId, String reason) {
        PlayerRef target = subject(targetId);
        Optional<String> given = Optional.of(Objects.requireNonNull(reason, "reason"));
        return offServer(target, () -> {
            Result<PlayerWarned, ModerationError> result = writes.warn().warn(actor(), target, given, silent);
            if (result.isErr()) {
                return UxmResult.failed(failure(result.errorOrThrow()));
            }
            return UxmResult.ok(ModerationQueries.view(result.orElseThrow().warn()));
        });
    }

    @Override
    public CompletableFuture<UxmResult<UxmSanction>> jail(UUID targetId, String jail, String reason) {
        return jailing(targetId, jail, "", Optional.of(Objects.requireNonNull(reason, "reason")));
    }

    @Override
    public CompletableFuture<UxmResult<UxmSanction>> jail(
            UUID targetId, String jail, Duration duration, String reason) {
        return jailing(targetId, jail, span(duration), Optional.of(Objects.requireNonNull(reason, "reason")));
    }

    @Override
    public CompletableFuture<UxmOutcome> unjail(UUID targetId) {
        PlayerRef target = subject(targetId);
        return offServer(target, () -> outcome(writes.unjail().unjail(actor(), target)));
    }

    private CompletableFuture<UxmResult<UxmSanction>> banning(UUID targetId, Optional<String> reason) {
        PlayerRef target = subject(targetId);
        return offServer(target, () -> sanction(targetId, writes.ban().ban(actor(), target, reason, silent)));
    }

    private CompletableFuture<UxmResult<UxmSanction>> tempBanning(
            UUID targetId, Duration duration, Optional<String> reason) {
        PlayerRef target = subject(targetId);
        String raw = span(duration);
        return offServer(
                target, () -> sanction(targetId, writes.tempBan().tempban(actor(), target, raw, reason, silent)));
    }

    private CompletableFuture<UxmResult<UxmSanction>> muting(UUID targetId, String raw, Optional<String> reason) {
        PlayerRef target = subject(targetId);
        return offServer(target, () -> {
            Result<MuteState, ModerationError> result = writes.mute().mute(actor(), target, raw, reason, silent);
            if (result.isErr()) {
                return UxmResult.failed(failure(result.errorOrThrow()));
            }
            // A mute that lands is one of the two active states; None here would mean the use case reported
            // success for a player it did not mute, which is worth saying rather than publishing as a success.
            return ModerationQueries.view(messages, targetId, result.orElseThrow())
                    .map(UxmResult::ok)
                    .orElseGet(() -> UxmResult.failed(UxmFailure.FAILED, "the mute was applied but reads as absent"));
        });
    }

    private CompletableFuture<UxmOutcome> kicking(UUID targetId, Optional<String> reason) {
        Objects.requireNonNull(targetId, "targetId");
        // The kick itself tells a player on another backend from one on none, and reaches the first there.
        PlayerRef target = subject(targetId);
        return offServer(target, () -> outcome(writes.kick().kick(actor(), target, reason, silent)));
    }

    private CompletableFuture<UxmResult<UxmSanction>> jailing(
            UUID targetId, String jail, String raw, Optional<String> reason) {
        PlayerRef target = subject(targetId);
        String named = Objects.requireNonNull(jail, "jail");
        return offServer(target, () -> {
            Result<JailState.Active, ModerationError> result = writes.jail().jail(actor(), target, named, raw, reason);
            return result.isErr()
                    ? UxmResult.failed(failure(result.errorOrThrow()))
                    : UxmResult.ok(ModerationQueries.view(targetId, result.orElseThrow()));
        });
    }

    private UxmResult<UxmSanction> sanction(UUID targetId, Result<TempbanState.Active, ModerationError> result) {
        return result.isErr()
                ? UxmResult.failed(failure(result.errorOrThrow()))
                : UxmResult.ok(ModerationQueries.view(messages, targetId, result.orElseThrow()));
    }

    /**
     * Run {@code write} about {@code target} on the storage lane, in order behind other work about them. The lane
     * reads the permission answers the write needs before it leaves, which is only safe on the server thread, so the
     * hand-off itself happens there.
     */
    private <T> CompletableFuture<T> offServer(PlayerRef target, Supplier<T> write) {
        CompletableFuture<T> answer = new CompletableFuture<>();
        scheduler.onGlobal(() -> {
            try {
                var unused = writes.lane().supply(actor(), target, write).whenComplete((stored, failure) -> {
                    if (failure == null) {
                        answer.complete(stored);
                    } else {
                        answer.completeExceptionally(failure);
                    }
                });
            } catch (RuntimeException failure) {
                answer.completeExceptionally(failure);
            }
        });
        return answer;
    }

    /** Which published code a moderation refusal is. */
    private static UxmFailure failure(ModerationError error) {
        return switch (error) {
            case TARGET_EXEMPT -> UxmFailure.of(UxmFailure.REFUSED, "that player is exempt from punishment");
            case TARGET_ELSEWHERE -> UxmFailure.of(UxmFailure.REFUSED, "that player is connected to another server");
            case TARGET_OFFLINE -> UxmFailure.of(UxmFailure.PLAYER_OFFLINE, "the player is not online");
            case ALREADY_MUTED -> UxmFailure.of(UxmFailure.ALREADY_IN_STATE, "that player is already muted for longer");
            case ALREADY_JAILED -> UxmFailure.of(UxmFailure.ALREADY_IN_STATE, "that player is already jailed");
            case LONGER_BAN_RUNNING ->
                UxmFailure.of(UxmFailure.ALREADY_IN_STATE, "that player is already banned for longer");
            case UNKNOWN_JAIL -> UxmFailure.of(UxmFailure.NOT_FOUND, "no jail by that name");
            case NOT_MUTED -> UxmFailure.of(UxmFailure.NOT_FOUND, "that player is not muted");
            case NOT_BANNED -> UxmFailure.of(UxmFailure.NOT_FOUND, "that player is not banned");
            case NOT_JAILED -> UxmFailure.of(UxmFailure.NOT_FOUND, "that player is not jailed");
            case UNKNOWN_TARGET -> UxmFailure.of(UxmFailure.NOT_FOUND, "the server has never seen that player");
            case SHARED_ADDRESS ->
                UxmFailure.of(UxmFailure.REFUSED, "that address is shared by every player behind geyser or a proxy");
            default ->
                UxmFailure.of(
                        UxmFailure.REFUSED,
                        "moderation refused it: " + error.name().toLowerCase(java.util.Locale.ROOT));
        };
    }

    private static UxmOutcome outcome(Result<Unit, ModerationError> result) {
        return result.isErr() ? UxmOutcome.failed(failure(result.errorOrThrow())) : UxmOutcome.ok();
    }

    private PlayerRef actor() {
        return ApiActors.of(source);
    }

    private PlayerRef subject(UUID targetId) {
        return ApiValues.subject(players, Objects.requireNonNull(targetId, "targetId"));
    }

    /** The span the use cases parse, so the API can take a {@link Duration} and they can keep their own grammar. */
    private static String span(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("a timed punishment must last some time: " + duration);
        }
        return SanctionDuration.format(duration);
    }
}

package com.uxplima.uxmessentials.moderation.adapter.outbound.api;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmModerationQuery;
import com.uxplima.uxmessentials.api.view.UxmIssuer;
import com.uxplima.uxmessentials.api.view.UxmSanction;
import com.uxplima.uxmessentials.api.view.UxmSanctionAction;
import com.uxplima.uxmessentials.api.view.UxmSanctionKind;
import com.uxplima.uxmessentials.api.view.UxmSanctionRecord;
import com.uxplima.uxmessentials.api.view.UxmWarn;
import com.uxplima.uxmessentials.moderation.application.Ban;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published moderation query, over the same repository the commands read.
 *
 * <p>Every answer is about the present moment: a lapsed punishment is reported as absent rather than as an
 * expired one, because a consumer asking "is this player muted" wants an answer it can act on, not a state
 * machine to interpret. The clock is injected so the boundary between "still serving" and "served" is the same
 * one the rest of the plugin uses.
 *
 * <p>Everything answers for an offline player. A punishment that could only be read while its subject was online
 * would be no use to the plugin that has to enforce it, which is usually the case precisely when they are not.
 */
@NullMarked
public final class ModerationQueries implements UxmModerationQuery {

    private final ModerationRepository repository;
    private final SanctionHistory history;
    private final PlayerLookup players;
    private final Scheduler scheduler;
    private final Clock clock;

    public ModerationQueries(
            ModerationRepository repository,
            SanctionHistory history,
            PlayerLookup players,
            Scheduler scheduler,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.history = Objects.requireNonNull(history, "history");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<Optional<UxmSanction>> ban(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> {
            Instant now = clock.instant();
            TempbanState state = repository.loadTempban(subject(playerId));
            if (!(state instanceof TempbanState.Active active) || !state.isActiveAt(now)) {
                return Optional.empty();
            }
            return Optional.of(view(playerId, active));
        });
    }

    @Override
    public CompletableFuture<Optional<UxmSanction>> mute(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> {
            Instant now = clock.instant();
            MuteState state = repository.loadMute(subject(playerId));
            if (!state.isActiveAt(now)) {
                return Optional.empty();
            }
            return view(playerId, state);
        });
    }

    @Override
    public CompletableFuture<Optional<UxmSanction>> jail(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> {
            Instant now = clock.instant();
            JailState state = repository.loadJail(subject(playerId));
            if (!(state instanceof JailState.Active active) || !state.isActiveAt(now)) {
                return Optional.empty();
            }
            return Optional.of(view(playerId, active));
        });
    }

    @Override
    public CompletableFuture<List<UxmWarn>> warns(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(
                scheduler,
                () -> repository.warns(subject(playerId), clock.instant()).stream()
                        .map(ModerationQueries::view)
                        .toList());
    }

    @Override
    public CompletableFuture<List<UxmSanctionRecord>> history(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least one: " + limit);
        }
        return AsyncQueries.supply(
                scheduler,
                () -> history.recentForTarget(playerId, limit).stream()
                        .map(ModerationQueries::view)
                        .toList());
    }

    private PlayerRef subject(UUID playerId) {
        return ApiValues.subject(players, playerId);
    }

    static UxmWarn view(Warn warn) {
        return new UxmWarn(issuer(warn.issuer()), warn.reason(), warn.issuedAt(), warn.expiresAt());
    }

    /** A ban as the API publishes it. Shared with the write side, so a ban reads the same however it was laid down. */
    static UxmSanction view(UUID playerId, TempbanState.Active ban) {
        // A permanent ban has no state of its own: it is stored as a span so far out that nobody will see it
        // lapse. Published, that has to be an absent expiry, or every consumer would have to know the trick to
        // tell a permanent ban from a ban ending in the year 3026.
        boolean permanent = !ban.until().isBefore(ban.issuedAt().plus(Ban.PERMANENT_SPAN));
        return new UxmSanction(
                UxmSanctionKind.BAN,
                playerId,
                issuer(ban.issuer()),
                ban.reason(),
                ban.issuedAt(),
                permanent ? Optional.empty() : Optional.of(ban.until()));
    }

    /** A mute as the API publishes it, or empty for the state that means "not muted". */
    static Optional<UxmSanction> view(UUID playerId, MuteState mute) {
        return switch (mute) {
            case MuteState.Permanent permanent ->
                Optional.of(new UxmSanction(
                        UxmSanctionKind.MUTE,
                        playerId,
                        issuer(permanent.issuer()),
                        permanent.reason(),
                        permanent.issuedAt(),
                        Optional.empty()));
            case MuteState.Timed timed ->
                Optional.of(new UxmSanction(
                        UxmSanctionKind.MUTE,
                        playerId,
                        issuer(timed.issuer()),
                        timed.reason(),
                        timed.issuedAt(),
                        Optional.of(timed.until())));
            case MuteState.None ignored -> Optional.empty();
        };
    }

    /** A jail sentence as the API publishes it; an online-only sentence has no wall-clock expiry to publish. */
    static UxmSanction view(UUID playerId, JailState.Active sentence) {
        return new UxmSanction(
                UxmSanctionKind.JAIL,
                playerId,
                issuer(sentence.issuer()),
                sentence.reason(),
                sentence.issuedAt(),
                sentence.until());
    }

    private static UxmSanctionRecord view(SanctionHistoryEntry entry) {
        return new UxmSanctionRecord(
                action(entry.action()),
                entry.target(),
                issuer(entry.actor()),
                entry.reason(),
                entry.at(),
                entry.expiry());
    }

    private static UxmIssuer issuer(Issuer issuer) {
        return new UxmIssuer(issuer.uuid(), issuer.name());
    }

    private static UxmSanctionAction action(SanctionAction action) {
        return switch (action) {
            case BAN -> UxmSanctionAction.BAN;
            case UNBAN -> UxmSanctionAction.UNBAN;
            case MUTE -> UxmSanctionAction.MUTE;
            case UNMUTE -> UxmSanctionAction.UNMUTE;
            case WARN -> UxmSanctionAction.WARN;
            case KICK -> UxmSanctionAction.KICK;
        };
    }
}

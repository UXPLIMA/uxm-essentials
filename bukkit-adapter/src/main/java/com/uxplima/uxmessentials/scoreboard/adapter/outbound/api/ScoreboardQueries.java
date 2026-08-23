package com.uxplima.uxmessentials.scoreboard.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;

import com.uxplima.uxmessentials.api.query.UxmScoreboardQuery;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderer;
import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published sidebar read, over the same preference {@code /scoreboard} flips and the render loop consults.
 *
 * <p>The preference is stored on the player, so the read hops to the thread that owns them and an absent player is
 * an empty answer. The store itself reports an offline player as hidden, which is the right answer for a render
 * loop deciding whether to draw a board and the wrong one to publish as a preference, so the two are told apart
 * here rather than passed through.
 */
@NullMarked
public final class ScoreboardQueries implements UxmScoreboardQuery {

    private final ScoreboardVisibilityStore visibility;
    private final PlayerLookup players;
    private final Scheduler scheduler;
    private final Optional<ScoreboardRenderer> renderer;

    public ScoreboardQueries(ScoreboardVisibilityStore visibility, PlayerLookup players, Scheduler scheduler) {
        this(visibility, players, scheduler, null);
    }

    public ScoreboardQueries(
            ScoreboardVisibilityStore visibility,
            PlayerLookup players,
            Scheduler scheduler,
            @org.jspecify.annotations.Nullable ScoreboardRenderer renderer) {
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.renderer = Optional.ofNullable(renderer);
    }

    @Override
    public CompletableFuture<Optional<String>> activeBoard(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerRef who = ApiValues.subject(players, playerId);
        return AsyncQueries.onPlayer(
                scheduler,
                who,
                () -> Bukkit.getPlayer(who.uuid()) == null
                        ? Optional.empty()
                        : renderer.flatMap(value -> value.appliedBoard(who)),
                Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<Boolean>> yielded(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerRef who = ApiValues.subject(players, playerId);
        return AsyncQueries.onPlayer(
                scheduler,
                who,
                () -> Bukkit.getPlayer(who.uuid()) == null
                        ? Optional.empty()
                        : renderer.map(value -> value.yielded(who.uuid())),
                Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<Boolean>> hidden(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerRef who = ApiValues.subject(players, playerId);
        return AsyncQueries.onPlayer(scheduler, who, () -> read(who), Optional.empty());
    }

    private Optional<Boolean> read(PlayerRef who) {
        if (Bukkit.getPlayer(who.uuid()) == null) {
            return Optional.empty();
        }
        return Optional.of(visibility.hidden(who));
    }
}

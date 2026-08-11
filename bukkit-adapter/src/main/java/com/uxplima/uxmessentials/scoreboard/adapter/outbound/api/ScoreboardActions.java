package com.uxplima.uxmessentials.scoreboard.adapter.outbound.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmScoreboardActions;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderer;
import com.uxplima.uxmessentials.scoreboard.application.ToggleScoreboard;
import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published sidebar writes, over the same use case and the same renderer {@code /scoreboard} uses.
 *
 * <p>Hiding and showing go through {@link ToggleScoreboard}, so the player is told what happened and the visibility
 * event is published exactly as it would be had they run the command themselves. The use case only knows how to
 * flip, so the current state is read first and a request for the state the player is already in is refused rather
 * than turned into its opposite.
 *
 * <p>Everything runs on the thread that owns the player: the preference lives on them, and swapping a board is only
 * valid from there.
 */
@NullMarked
public final class ScoreboardActions implements UxmScoreboardActions {

    private final ToggleScoreboard toggle;
    private final ScoreboardVisibilityStore visibility;
    private final ScoreboardRenderer renderer;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public ScoreboardActions(
            ToggleScoreboard toggle,
            ScoreboardVisibilityStore visibility,
            ScoreboardRenderer renderer,
            PlayerLookup players,
            Scheduler scheduler) {
        this.toggle = Objects.requireNonNull(toggle, "toggle");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> refresh(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerRef who = ApiValues.subject(players, playerId);
        return onPlayer(who, player -> {
            renderer.renderFor(player);
            return UxmOutcome.ok();
        });
    }

    @Override
    public CompletableFuture<UxmOutcome> hide(UUID playerId) {
        return set(playerId, true);
    }

    @Override
    public CompletableFuture<UxmOutcome> show(UUID playerId) {
        return set(playerId, false);
    }

    /** Flip to {@code hidden} when the player is not already there, then draw or tear down the board to match. */
    private CompletableFuture<UxmOutcome> set(UUID playerId, boolean hidden) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerRef who = ApiValues.subject(players, playerId);
        return onPlayer(who, player -> {
            if (visibility.hidden(who) == hidden) {
                return UxmOutcome.failed(
                        UxmFailure.ALREADY_IN_STATE,
                        hidden ? "their sidebar is already away" : "their sidebar is already shown");
            }
            boolean nowHidden = toggle.toggle(who);
            if (nowHidden) {
                renderer.clear(player);
            } else {
                renderer.renderFor(player);
            }
            return UxmOutcome.ok();
        });
    }

    /** Run {@code write} on the thread that owns the player, or answer offline when they are not here. */
    private CompletableFuture<UxmOutcome> onPlayer(PlayerRef who, Function<Player, UxmOutcome> write) {
        UxmOutcome gone = UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "a sidebar belongs to a player who is here");
        return AsyncActions.onPlayer(
                scheduler,
                who,
                () -> {
                    Player player = Bukkit.getPlayer(who.uuid());
                    return player == null ? gone : write.apply(player);
                },
                gone);
    }
}

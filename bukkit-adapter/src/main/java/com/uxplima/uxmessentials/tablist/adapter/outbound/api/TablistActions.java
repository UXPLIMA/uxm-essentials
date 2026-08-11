package com.uxplima.uxmessentials.tablist.adapter.outbound.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmTablistActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.tablist.adapter.outbound.TablistRenderer;
import org.jspecify.annotations.NullMarked;

/**
 * The published tab-list write: one repaint, run through the same renderer the refresh timer runs.
 *
 * <p>It is the timer's own pass for a single viewer, brought forward. The header, footer, list names and any filler
 * rows are recomputed from the current config and placeholder values, so a caller that has just changed something
 * the list reads sees it immediately instead of within the refresh interval.
 *
 * <p>The repaint runs on the thread that owns the viewer, which is the only one allowed to touch them on Folia.
 */
@NullMarked
public final class TablistActions implements UxmTablistActions {

    private final TablistRenderer renderer;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public TablistActions(TablistRenderer renderer, PlayerLookup players, Scheduler scheduler) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> refresh(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerRef who = ApiValues.subject(players, playerId);
        UxmOutcome gone = UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "a tab list belongs to a player who is here");
        return AsyncActions.onPlayer(
                scheduler,
                who,
                () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null) {
                        return gone;
                    }
                    renderer.renderFor(player);
                    return UxmOutcome.ok();
                },
                gone);
    }
}

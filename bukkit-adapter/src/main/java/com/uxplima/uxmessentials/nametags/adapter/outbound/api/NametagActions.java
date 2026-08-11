package com.uxplima.uxmessentials.nametags.adapter.outbound.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmNametagActions;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.nametags.adapter.outbound.PacketNametagPresenter;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published nametag write: the reconcile pass the timer runs for everybody, run now for one wearer.
 *
 * <p>{@link PacketNametagPresenter#update} re-selects which format applies from the wearer's current permissions,
 * world and state before it redraws, so a caller that has just given somebody a rank gets the new format rather
 * than a redraw of the old one. A wearer no format applies to has their nametag removed by the same pass, which is
 * the correct outcome and still reported as success.
 *
 * <p>It runs on the thread that owns the wearer, matching the reconcile timer.
 */
@NullMarked
public final class NametagActions implements UxmNametagActions {

    private final PacketNametagPresenter presenter;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public NametagActions(PacketNametagPresenter presenter, PlayerLookup players, Scheduler scheduler) {
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> refresh(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerRef who = ApiValues.subject(players, playerId);
        UxmOutcome gone = UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "a nametag belongs to a player who is here");
        return AsyncActions.onPlayer(
                scheduler,
                who,
                () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null) {
                        return gone;
                    }
                    presenter.update(player);
                    return UxmOutcome.ok();
                },
                gone);
    }
}

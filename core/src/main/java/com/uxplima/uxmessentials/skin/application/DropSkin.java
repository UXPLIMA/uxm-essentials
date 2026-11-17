package com.uxplima.uxmessentials.skin.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import org.jspecify.annotations.NullMarked;

/**
 * Deletes a stored skin outright, the staff counterpart to a player clearing their own.
 *
 * <p>A drop is aimed at somebody who may well be offline. One who is online here is dressed at once in what the join
 * order resolves, as a clear does; anybody else gets it on their next join, or from the server they are on when it
 * hears of the drop. It is the answer to a stored texture that is wrong or unwanted.
 */
@NullMarked
public final class DropSkin {

    private final SkinRepository repository;
    private final DressLogin dressLogin;
    private final SkinView view;
    private final PlayerLookup players;

    public DropSkin(SkinRepository repository, DressLogin dressLogin, SkinView view, PlayerLookup players) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dressLogin = Objects.requireNonNull(dressLogin, "dressLogin");
        this.view = Objects.requireNonNull(view, "view");
        this.players = Objects.requireNonNull(players, "players");
    }

    /** Delete {@code player}'s stored skin, reporting whether there was one, and dress them again if they are here. */
    public Outcome drop(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        if (repository.find(player.uuid()).isEmpty()) {
            return Outcome.NOTHING_STORED;
        }
        repository.delete(player.uuid());
        if (players.isOnline(player.uuid())) {
            dressLogin
                    .resolve(player.uuid(), player.name())
                    .ifPresent(dressed -> view.apply(player, dressed.texture(), dressed.model()));
        }
        return Outcome.DROPPED;
    }

    /** What became of a drop. */
    public enum Outcome {
        /** The row is gone and the player wears what the join order resolves. */
        DROPPED,
        /** There was nothing stored for that player. */
        NOTHING_STORED
    }
}

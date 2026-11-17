package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmPlayersQuery;
import com.uxplima.uxmessentials.api.view.UxmPlayer;
import com.uxplima.uxmessentials.api.view.UxmPlayerMatch;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published name lookup, over the same {@link PlayerLookup} a command resolves a typed name with, so a web store
 * and {@code /pay} agree on who a name is.
 *
 * <p>On a worker: off the tick the lookup may read the durable name store, which is what settles a name typed in
 * another case on an offline-mode server and finds an account that played only on another server.
 */
@NullMarked
public final class PlayerQueries implements UxmPlayersQuery {

    private final PlayerLookup players;
    private final Scheduler scheduler;

    public PlayerQueries(PlayerLookup players, Scheduler scheduler) {
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmPlayerMatch> byName(String name) {
        Objects.requireNonNull(name, "name");
        String typed = name.strip();
        if (typed.isEmpty()) {
            return CompletableFuture.completedFuture(new UxmPlayerMatch.Unknown());
        }
        return AsyncQueries.supply(scheduler, () -> view(players.matchByName(typed)));
    }

    private static UxmPlayerMatch view(NameMatch match) {
        return switch (match) {
            case NameMatch.Found found -> new UxmPlayerMatch.Found(view(found.ref()));
            case NameMatch.Unknown ignored -> new UxmPlayerMatch.Unknown();
            case NameMatch.Ambiguous ambiguous ->
                new UxmPlayerMatch.Ambiguous(
                        ambiguous.typed(),
                        ambiguous.variants().stream().map(PlayerQueries::view).toList());
        };
    }

    private static UxmPlayer view(PlayerRef player) {
        return new UxmPlayer(player.uuid(), player.name());
    }
}

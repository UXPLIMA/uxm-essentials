package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.application.port.KnownPlayers;
import com.uxplima.uxmessentials.persistence.lookup.JooqPlayerNameRepository;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import org.jspecify.annotations.NullMarked;

/**
 * Adds the players this server knows from its own user cache to the shared name list before a sweep, so a player who
 * last joined before the list was written on this server is reached too. A Bedrock player through Floodgate is in that
 * cache with the id and the prefixed name Floodgate gives them. The server's list is read on the global region, the
 * one thread allowed to ask for it, and written here off the tick; rows a join wrote are left as they are.
 */
@NullMarked
public final class ServerKnownPlayers implements KnownPlayers {

    private static final Duration WAIT = Duration.ofSeconds(30);

    private final Server server;
    private final Scheduler scheduler;
    private final JooqPlayerNameRepository names;
    private final Logger log;

    public ServerKnownPlayers(Server server, Scheduler scheduler, JooqPlayerNameRepository names, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.names = Objects.requireNonNull(names, "names");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void recordAll() {
        CompletableFuture<List<PlayerName>> known = new CompletableFuture<>();
        scheduler.onGlobal(() -> {
            try {
                List<PlayerName> players = new ArrayList<>();
                for (OfflinePlayer account : server.getOfflinePlayers()) {
                    String name = account.getName();
                    if (name != null) {
                        players.add(new PlayerName(account.getUniqueId(), name, account.getLastSeen()));
                    }
                }
                known.complete(players);
            } catch (RuntimeException failure) {
                known.completeExceptionally(failure);
            }
        });
        List<PlayerName> players;
        try {
            players = known.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException | TimeoutException failure) {
            log.warn("event=known_players_unread reason={}", failure.toString());
            return;
        }
        int added = names.addMissing(players);
        if (added > 0) {
            log.info("event=known_players_recorded added={}", added);
        }
    }
}

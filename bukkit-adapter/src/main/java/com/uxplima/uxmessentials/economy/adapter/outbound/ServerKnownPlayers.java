package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.application.port.KnownPlayers;
import com.uxplima.uxmessentials.persistence.lookup.JooqPlayerNameRepository;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import org.jspecify.annotations.NullMarked;

/**
 * Adds the players this server knows from its own player data to the shared name list before a sweep, so a player who
 * last joined before the list was written on this server is reached too. A Bedrock player through Floodgate is there
 * with the id and the prefixed name Floodgate gives them.
 *
 * <p>Only the ids are taken on the global region, the one thread allowed to list the server's players. A name and a
 * last-seen time of a player who is offline come from their data file, so they are read here off the tick, a chunk at
 * a time, and only for the players the list lacks; a large server's tick never waits on its disk for this.
 */
@NullMarked
public final class ServerKnownPlayers implements KnownPlayers {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final int CHUNK = 500;

    private final Scheduler scheduler;
    private final Supplier<List<UUID>> ids;
    private final Function<UUID, Optional<PlayerName>> read;
    private final Supplier<Set<UUID>> known;
    private final ToIntFunction<List<PlayerName>> addMissing;
    private final Logger log;

    public ServerKnownPlayers(Server server, Scheduler scheduler, JooqPlayerNameRepository names, Logger log) {
        this(
                scheduler,
                () -> {
                    List<UUID> all = new ArrayList<>();
                    for (OfflinePlayer account : server.getOfflinePlayers()) {
                        all.add(account.getUniqueId());
                    }
                    return all;
                },
                id -> {
                    OfflinePlayer account = server.getOfflinePlayer(id);
                    String name = account.getName();
                    return name == null
                            ? Optional.empty()
                            : Optional.of(new PlayerName(id, name, account.getLastSeen()));
                },
                names::knownUuids,
                names::addMissing,
                log);
    }

    /**
     * @param ids every player the server holds data for, read on the global region
     * @param read one player's name and last-seen time, read off the tick; empty when the server has no name
     * @param known the players the shared list already holds
     * @param addMissing adds rows the list lacks, answering how many it added
     */
    public ServerKnownPlayers(
            Scheduler scheduler,
            Supplier<List<UUID>> ids,
            Function<UUID, Optional<PlayerName>> read,
            Supplier<Set<UUID>> known,
            ToIntFunction<List<PlayerName>> addMissing,
            Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.read = Objects.requireNonNull(read, "read");
        this.known = Objects.requireNonNull(known, "known");
        this.addMissing = Objects.requireNonNull(addMissing, "addMissing");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Off the tick: the caller is a sweep starting on the async lane. */
    @Override
    public void recordAll() {
        CompletableFuture<List<UUID>> listed = new CompletableFuture<>();
        scheduler.onGlobal(() -> {
            try {
                listed.complete(List.copyOf(ids.get()));
            } catch (RuntimeException failure) {
                listed.completeExceptionally(failure);
            }
        });
        List<UUID> all;
        try {
            all = listed.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException | TimeoutException failure) {
            log.warn("event=known_players_unread reason={}", failure.toString());
            return;
        }
        Set<UUID> have = known.get();
        List<UUID> missing = new ArrayList<>();
        for (UUID id : all) {
            if (!have.contains(id)) {
                missing.add(id);
            }
        }
        int added = 0;
        for (int from = 0; from < missing.size(); from += CHUNK) {
            List<PlayerName> chunk = new ArrayList<>();
            for (UUID id : missing.subList(from, Math.min(missing.size(), from + CHUNK))) {
                try {
                    read.apply(id).ifPresent(chunk::add);
                } catch (RuntimeException unreadable) {
                    // One unreadable data file must not keep the others off the list.
                    log.warn("event=known_player_unread id={} reason={}", id, unreadable.toString());
                }
            }
            added += addMissing.applyAsInt(chunk);
        }
        if (added > 0) {
            log.info("event=known_players_recorded added={}", added);
        }
    }
}

package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;

import com.uxplima.uxmessentials.moderation.application.CaseVariantSanctions;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The earliest sign an account existed, from everything that keeps one: the first join the moderation seen row
 * records, the name list's row (written at every join, and filled from the server's player files the first time the
 * plugin ran), and the first play time in the server's own player file. Any of them from before a sanction proves the
 * account was not made to get around it; only an account with no trace anywhere is new.
 *
 * <p>Reads the database and the server's player file, so it runs off the tick.
 */
@NullMarked
public final class KnownAccountTraces implements CaseVariantSanctions.AccountTraces {

    /** The rows read under the account's name to find its own; far more than any name's spellings. */
    private static final int NAME_ROWS = 32;

    private final ModerationRepository repository;
    private final PlayerNameRepository names;
    private final Server server;

    public KnownAccountTraces(ModerationRepository repository, PlayerNameRepository names, Server server) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.names = Objects.requireNonNull(names, "names");
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Optional<Instant> earliest(PlayerRef account) {
        Objects.requireNonNull(account, "account");
        List<Instant> traces = new ArrayList<>();
        repository.seen(account).map(SeenRecord::firstSeen).ifPresent(traces::add);
        for (PlayerName row : names.underName(account.name(), NAME_ROWS)) {
            if (row.uuid().equals(account.uuid())) {
                traces.add(Instant.ofEpochMilli(row.lastSeen()));
            }
        }
        long firstPlayed = server.getOfflinePlayer(account.uuid()).getFirstPlayed();
        if (firstPlayed > 0) {
            traces.add(Instant.ofEpochMilli(firstPlayed));
        }
        return traces.stream().min(Comparator.naturalOrder());
    }
}

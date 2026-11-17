package com.uxplima.uxmessentials.shared.application.message;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.PendingPlayerNotices;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.PlayerNotices;
import com.uxplima.uxmessentials.shared.domain.PlayerNotice;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;

/**
 * The {@link PlayerNotices} over {@link PendingPlayerNotices}: a player online here is told at once; a line for one
 * who is not is kept in the shared database, and {@link #deliver} shows it when they are next online here, as they
 * join or when the server that kept it announces it. Taking a kept notice removes it, so it is shown once. A player
 * who left between the take and the line keeps it for their next time online.
 *
 * <p>Every method runs off the tick: it reads and writes the database.
 */
public final class DeferredPlayerNotices implements PlayerNotices {

    private final PendingPlayerNotices pending;
    private final PlayerLookup players;
    private final Notifier notifier;
    private final Clock clock;

    public DeferredPlayerNotices(PendingPlayerNotices pending, PlayerLookup players, Notifier notifier, Clock clock) {
        this.pending = Objects.requireNonNull(pending, "pending");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void tell(PlayerRef recipient, MessageLine line) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(line, "line");
        if (players.isOnline(recipient.uuid())) {
            notifier.send(recipient, line);
            return;
        }
        pending.keep(new PlayerNotice(recipient.uuid(), line, clock.instant()));
    }

    /** Show {@code recipient}, online here, the lines kept for them while they were away; returns how many. */
    public int deliver(PlayerRef recipient) {
        Objects.requireNonNull(recipient, "recipient");
        List<PlayerNotice> kept = pending.take(recipient.uuid());
        for (PlayerNotice notice : kept) {
            if (players.isOnline(recipient.uuid())) {
                notifier.send(recipient, notice.line());
            } else {
                pending.keep(notice);
            }
        }
        return kept.size();
    }
}

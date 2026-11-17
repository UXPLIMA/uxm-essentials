package com.uxplima.uxmessentials.shared.application.message;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PendingPlayerNotices;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.PlayerNotices;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerNotice;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;

/**
 * The {@link PlayerNotices} over {@link PendingPlayerNotices}: a player online here is told at once; a line for one
 * who is not is kept in the shared database, and {@link #deliver} shows it when they are next online here, as they
 * join or when the server that kept it announces it. Taking a kept notice removes it, so it is shown once. A player
 * who left between the take and the line keeps it for their next time online.
 *
 * <p>A notice never fails what it is about: telling a player follows something already done, such as money already
 * moved, so a store that cannot keep or hand back a line is logged and the caller carries on. A player who comes back
 * to many lines is shown the newest {@code shownAtOnce} of them and told how many more there were, so a pile of lines
 * cannot flood their chat.
 *
 * <p>Every method runs off the tick: it reads and writes the database.
 */
public final class DeferredPlayerNotices implements PlayerNotices {

    private final PendingPlayerNotices pending;
    private final PlayerLookup players;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final Logger log;
    private final Clock clock;
    private final int shownAtOnce;

    /**
     * @param scheduler the lane a line that could not reach its player is kept on
     * @param shownAtOnce how many kept lines a returning player is shown, at least one
     */
    public DeferredPlayerNotices(
            PendingPlayerNotices pending,
            PlayerLookup players,
            Notifier notifier,
            Scheduler scheduler,
            Logger log,
            Clock clock,
            int shownAtOnce) {
        this.pending = Objects.requireNonNull(pending, "pending");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (shownAtOnce < 1) {
            throw new IllegalArgumentException("shownAtOnce must be at least 1: " + shownAtOnce);
        }
        this.shownAtOnce = shownAtOnce;
    }

    @Override
    public void tell(PlayerRef recipient, MessageLine line) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(line, "line");
        PlayerNotice notice = new PlayerNotice(recipient.uuid(), line, clock.instant());
        boolean shown;
        try {
            shown = players.isOnline(recipient.uuid()) && show(recipient, notice);
        } catch (RuntimeException failure) {
            log.error("could not tell whether player " + recipient.uuid() + " is online", failure);
            shown = false;
        }
        if (!shown) {
            keep(notice);
        }
    }

    /** Show {@code recipient}, online here, the lines kept for them while they were away; returns how many. */
    public int deliver(PlayerRef recipient) {
        Objects.requireNonNull(recipient, "recipient");
        List<PlayerNotice> kept;
        try {
            kept = pending.take(recipient.uuid());
        } catch (RuntimeException failure) {
            // Nothing was handed back, so nothing is lost: the lines stay kept for the next take.
            log.error("could not read the lines kept for player " + recipient.uuid(), failure);
            return 0;
        }
        int left = Math.max(0, kept.size() - shownAtOnce);
        // The newest lines are the ones shown; the older ones are counted in one line ahead of them.
        List<PlayerNotice> shown = kept.subList(left, kept.size());
        if (left > 0) {
            List<PlayerNotice> older = List.copyOf(kept.subList(0, left));
            MessageLine more =
                    new MessageLine(SharedMessageKey.COMMON_NOTICES_MORE, Map.of("count", Integer.toString(left)));
            // The count stands for the older lines, so if it cannot reach the player they are kept instead.
            if (!players.isOnline(recipient.uuid()) || !show(recipient, more, () -> older.forEach(this::keep))) {
                older.forEach(this::keep);
            }
        }
        for (PlayerNotice notice : shown) {
            if (!players.isOnline(recipient.uuid()) || !show(recipient, notice)) {
                keep(notice);
            }
        }
        return kept.size();
    }

    /**
     * Show {@code notice}; false when showing it failed, so the caller keeps it rather than lose it. A line that turns
     * out not to reach the player (still logging in, or quitting) is kept on the async lane.
     */
    private boolean show(PlayerRef recipient, PlayerNotice notice) {
        return show(recipient, notice.line(), () -> keep(notice));
    }

    /** Show {@code line}, running {@code unshown} on the async lane when it cannot reach the player; false on failure. */
    private boolean show(PlayerRef recipient, MessageLine line, Runnable unshown) {
        try {
            notifier.send(recipient, line, () -> scheduler.async(unshown));
            return true;
        } catch (RuntimeException failure) {
            log.error(
                    "could not show player " + recipient.uuid() + " "
                            + line.key().key(),
                    failure);
            return false;
        }
    }

    /** Keep {@code notice}, logging rather than throwing when the store refuses it. */
    private void keep(PlayerNotice notice) {
        try {
            pending.keep(notice);
        } catch (RuntimeException failure) {
            log.error(
                    "could not keep a line for player " + notice.recipient() + " "
                            + notice.line().key().key(),
                    failure);
        }
    }
}

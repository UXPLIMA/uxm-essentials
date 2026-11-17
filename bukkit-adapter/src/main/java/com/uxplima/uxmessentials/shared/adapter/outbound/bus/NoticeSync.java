package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PendingPlayerNotices;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerNotice;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.PlayerNoticesKept;
import org.jspecify.annotations.NullMarked;

/**
 * The cross-server seam for kept player notices, the same shape as the kept rank actions in {@link RankSync}: a store
 * decorator that announces each kept line as a {@link PlayerNoticesKept} frame, and a listener that, on that frame,
 * shows the player their kept lines when they are online here. Taking a kept line removes it, so the backend the
 * player is on and a later join never both show it.
 */
@NullMarked
public final class NoticeSync {

    private NoticeSync() {}

    /** A {@link PendingPlayerNotices} store that announces each kept line, so the backend the player is on shows it. */
    public static PendingPlayerNotices pending(PendingPlayerNotices delegate, BusPublisher bus, Logger log) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(log, "log");
        return new Announcing(delegate, bus, log);
    }

    /**
     * A listener that, on a remote {@link PlayerNoticesKept} for a player online here, hands that player to
     * {@code deliver} on the async lane, the same delivery a join runs.
     */
    public static RemoteSyncListener listener(Consumer<PlayerRef> deliver, PlayerLookup players, Scheduler scheduler) {
        Objects.requireNonNull(deliver, "deliver");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(scheduler, "scheduler");
        return message -> {
            if (message instanceof PlayerNoticesKept kept && players.isOnline(kept.recipient())) {
                players.findByUuid(kept.recipient())
                        .ifPresent(recipient -> scheduler.async(() -> deliver.accept(recipient)));
            }
        };
    }

    /** Forwards to the store, then announces each kept line on the bus. */
    private static final class Announcing implements PendingPlayerNotices {

        private final PendingPlayerNotices delegate;
        private final BusPublisher bus;
        private final Logger log;

        Announcing(PendingPlayerNotices delegate, BusPublisher bus, Logger log) {
            this.delegate = delegate;
            this.bus = bus;
            this.log = log;
        }

        @Override
        public void keep(PlayerNotice notice) {
            delegate.keep(notice);
            // The row is committed by now. A lost announcement only means the line waits for the player's next join.
            try {
                bus.publish(new PlayerNoticesKept(bus.serverId(), notice.recipient()));
            } catch (RuntimeException failure) {
                log.error("failed to announce the kept notice of player " + notice.recipient(), failure);
            }
        }

        @Override
        public List<PlayerNotice> take(UUID recipient) {
            return delegate.take(recipient);
        }

        @Override
        public int purgeOlderThan(Instant cutoff) {
            return delegate.purgeOlderThan(cutoff);
        }
    }
}

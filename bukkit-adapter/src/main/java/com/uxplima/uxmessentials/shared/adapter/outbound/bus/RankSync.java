package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.application.CachedPlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.RankActions;
import com.uxplima.uxmessentials.ranks.application.port.PendingRankActions;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.network.RankActionsKept;
import com.uxplima.uxmessentials.shared.network.RankChanged;
import org.jspecify.annotations.NullMarked;

/**
 * The ranks context's cross-server sync seam, the same shape as {@link HomeSync}: a write decorator that announces
 * each saved rank pointer as a {@link RankChanged} frame, and a listener that, on a remote frame, makes the local
 * {@link CachedPlayerRankRepository} re-read that player. The cache holds only online players, so the reload is a
 * no-op on every backend the player is not on.
 */
@NullMarked
public final class RankSync {

    private RankSync() {}

    /** A {@link PlayerRankRepository} that broadcasts a {@link RankChanged} after every local save. */
    public static PlayerRankRepository repository(CachedPlayerRankRepository delegate, BusPublisher bus, Logger log) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(log, "log");
        return new Broadcasting(delegate, bus, log);
    }

    /**
     * A {@link PendingRankActions} store that announces each kept batch as a {@link RankActionsKept} frame, so the
     * backend the player is on runs it now rather than at their next join.
     */
    public static PendingRankActions pendingActions(PendingRankActions delegate, BusPublisher bus, Logger log) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(log, "log");
        return new AnnouncingPending(delegate, bus, log);
    }

    /**
     * A listener that, on a remote {@link RankActionsKept} for a player online here, runs what was kept for them, the
     * same take a join does, so each line still runs on one server only.
     */
    public static RemoteSyncListener actionsListener(RankActions actions, PlayerLookup players) {
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(players, "players");
        return message -> {
            if (message instanceof RankActionsKept kept && players.isOnline(kept.player())) {
                players.findByUuid(kept.player()).ifPresent(actions::joined);
            }
        };
    }

    /** A listener that reloads the affected player's cached pointer on a remote {@link RankChanged}. */
    public static RemoteSyncListener listener(CachedPlayerRankRepository cache) {
        Objects.requireNonNull(cache, "cache");
        return message -> {
            if (message instanceof RankChanged changed) {
                cache.refresh(changed.player());
            }
        };
    }

    /** Forwards every call to the cached delegate, then announces each save on the bus. */
    private static final class Broadcasting implements PlayerRankRepository {

        private final PlayerRankRepository delegate;
        private final BusPublisher bus;
        private final Logger log;

        Broadcasting(PlayerRankRepository delegate, BusPublisher bus, Logger log) {
            this.delegate = delegate;
            this.bus = bus;
            this.log = log;
        }

        @Override
        public Optional<PlayerRank> find(UUID playerId) {
            return delegate.find(playerId);
        }

        @Override
        public void save(UUID playerId, RankId rankId, Prestige prestige) {
            delegate.save(playerId, rankId, prestige);
            // The row is committed by now. A failed announcement must not reach the caller, which would read it as a
            // failed save and refund a rankup that did happen; peers catch up on the player's next login there.
            try {
                bus.publish(new RankChanged(bus.serverId(), playerId));
            } catch (RuntimeException failure) {
                log.error("failed to announce the rank change of player " + playerId, failure);
            }
        }

        @Override
        public Map<UUID, Integer> prestiged() {
            return delegate.prestiged();
        }
    }

    /** Forwards to the store, then announces each kept batch on the bus. */
    private static final class AnnouncingPending implements PendingRankActions {

        private final PendingRankActions delegate;
        private final BusPublisher bus;
        private final Logger log;

        AnnouncingPending(PendingRankActions delegate, BusPublisher bus, Logger log) {
            this.delegate = delegate;
            this.bus = bus;
            this.log = log;
        }

        @Override
        public void keep(UUID player, List<String> lines) {
            delegate.keep(player, lines);
            // The rows are committed by now. A lost announcement only means the lines wait for the player's next join.
            try {
                bus.publish(new RankActionsKept(bus.serverId(), player));
            } catch (RuntimeException failure) {
                log.error("failed to announce the kept rank actions of player " + player, failure);
            }
        }

        @Override
        public List<String> take(UUID player) {
            return delegate.take(player);
        }
    }
}

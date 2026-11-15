package com.uxplima.uxmessentials.playerwarps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpDeleted;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.junit.jupiter.api.Test;

/**
 * The hard delete at the end of the rent lifecycle: which archived warps are deleted for good, and when. Each pass
 * runs the way the rent sweep runs it, the due page first and then each warp on it.
 */
class PurgeArchivedRentWarpsTest {

    private static final Duration DELAY = Duration.ofDays(30);

    private final MutableClock clock = new MutableClock(NOW);
    private final Warps warps = new Warps();
    private final RecordingEvents events = new RecordingEvents();

    private PurgeArchivedRentWarps purge(Duration delay, Set<String> exemptPlayers) {
        RentConfig config = new RentConfig(
                true,
                BigDecimal.TEN,
                "default",
                Duration.ofDays(7),
                Duration.ofDays(3),
                delay,
                List.of(),
                exemptPlayers,
                Set.of(),
                Set.of());
        return new PurgeArchivedRentWarps(warps, events, config, clock);
    }

    private static int pass(PurgeArchivedRentWarps purge) {
        int deleted = 0;
        for (PlayerWarp warp : purge.due(500)) {
            if (purge.purge(warp)) {
                deleted++;
            }
        }
        return deleted;
    }

    private static PlayerWarp warp(long id, PlayerRef owner, String name) {
        return PlayerWarp.create(
                        owner, owner.name(), PlayerWarpName.of(name), at(0, 64, 0), NOW.minus(Duration.ofDays(60)))
                .withId(PlayerWarpId.of(id));
    }

    /** A warp the rent sweep archived at {@code archivedAt}, once its grace deadline had passed. */
    private static PlayerWarp archivedForRent(long id, PlayerRef owner, String name, Instant archivedAt) {
        RentState unpaid = new RentState(
                archivedAt.minus(Duration.ofDays(3)),
                Optional.of(archivedAt.minus(Duration.ofDays(3))),
                Optional.of(archivedAt));
        return warp(id, owner, name).withRent(unpaid, archivedAt).withStatus(WarpStatus.ARCHIVED, archivedAt);
    }

    @Test
    void aWarpArchivedForRentIsDeletedForGoodOnceTheDelayHasPassed() {
        PurgeArchivedRentWarps purge = purge(DELAY, Set.of());
        warps.put(archivedForRent(1, SAM, "market", NOW));

        clock.advance(DELAY.minusSeconds(1));
        assertThat(pass(purge)).isZero();
        assertThat(warps.findById(PlayerWarpId.of(1))).isPresent();

        clock.advance(Duration.ofSeconds(1));
        assertThat(pass(purge)).isEqualTo(1);
        assertThat(warps.findById(PlayerWarpId.of(1))).isEmpty();
        assertThat(events.published()).containsExactly(new PlayerWarpDeleted(SAM, PlayerWarpName.of("market")));
    }

    @Test
    void aZeroDelayKeepsArchivedWarpsForever() {
        PurgeArchivedRentWarps purge = purge(Duration.ZERO, Set.of());
        warps.put(archivedForRent(1, SAM, "market", NOW));

        clock.advance(Duration.ofDays(3650));

        assertThat(pass(purge)).isZero();
        assertThat(warps.findById(PlayerWarpId.of(1))).isPresent();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void onlyAWarpArchivedForRentAndLeftAloneIsDeleted() {
        PurgeArchivedRentWarps purge = purge(DELAY, Set.of("alex"));
        Instant longAgo = NOW.minus(Duration.ofDays(40));
        warps.put(archivedForRent(1, SAM, "market", longAgo));
        // Archived by its owner while paid up: no rent archive deadline.
        warps.put(warp(2, SAM, "home").withStatus(WarpStatus.ARCHIVED, longAgo));
        // Restored by an admin after the rent archive, so it is live again.
        warps.put(archivedForRent(3, SAM, "farm", longAgo).withStatus(WarpStatus.ACTIVE, longAgo));
        // Restored and archived again recently: the delay counts from that last change.
        warps.put(archivedForRent(4, SAM, "shop", longAgo).withStatus(WarpStatus.ARCHIVED, NOW));
        // Exempt owner: the rent sweep leaves the warp alone.
        warps.put(archivedForRent(5, ALEX, "tower", longAgo));

        assertThat(pass(purge)).isEqualTo(1);

        assertThat(warps.ids()).containsExactlyInAnyOrder(2L, 3L, 4L, 5L);
    }

    /** An in-memory store whose rent hard-delete query follows the port contract. */
    private static final class Warps implements PlayerWarpRepository {

        private final Map<PlayerWarpId, PlayerWarp> byId = new HashMap<>();

        void put(PlayerWarp warp) {
            byId.put(warp.id().orElseThrow(), warp);
        }

        List<Long> ids() {
            return byId.keySet().stream().map(PlayerWarpId::value).toList();
        }

        @Override
        public List<PlayerWarp> archivedForRent(Instant cutoff, int limit) {
            return byId.values().stream()
                    .filter(w -> w.status() == WarpStatus.ARCHIVED)
                    .filter(w -> !w.updatedAt().isAfter(cutoff))
                    .filter(w -> w.rent()
                            .flatMap(RentState::archiveAfter)
                            .map(deadline -> !deadline.isAfter(cutoff))
                            .orElse(false))
                    .sorted(Comparator.comparing((PlayerWarp w) ->
                            w.rent().flatMap(RentState::archiveAfter).orElseThrow()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            return byId.values().stream().filter(w -> w.name().equals(name)).findFirst();
        }

        @Override
        public Optional<PlayerWarp> findById(PlayerWarpId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            return byId.values().stream().filter(w -> w.owner().equals(owner)).toList();
        }

        @Override
        public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
            return ownedBy(owner);
        }

        @Override
        public int count(PlayerRef owner) {
            return ownedBy(owner).size();
        }

        @Override
        public boolean existsByName(PlayerWarpName name) {
            return findByName(name).isPresent();
        }

        @Override
        public PlayerWarpId save(PlayerWarp warp) {
            put(warp);
            return warp.id().orElseThrow();
        }

        @Override
        public void deleteById(PlayerWarpId id) {
            byId.remove(id);
        }

        @Override
        public void recordVisit(PlayerWarpId id) {}

        @Override
        public void updateRating(PlayerWarpId id, RatingSummary summary) {}

        @Override
        public void refreshFavouriteCount(PlayerWarpId id) {}
    }
}

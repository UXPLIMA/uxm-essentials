package com.uxplima.uxmessentials.playerwarps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.RentArchiveCursor;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpDeleted;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.junit.jupiter.api.Test;

/**
 * The hard delete at the end of the rent lifecycle: which archived warps are deleted for good, when, what happens to
 * money still in their bank, and how the pass behaves when the warp changes under it or a step fails.
 */
class PurgeArchivedRentWarpsTest {

    private static final Duration DELAY = Duration.ofDays(30);
    private static final Instant LONG_AGO = NOW.minus(Duration.ofDays(40));
    private static final WorldRef NETHER =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000bbbb"), "nether");

    private final MutableClock clock = new MutableClock(NOW);
    private final Warps warps = new Warps();
    private final Purse purse = new Purse(warps);
    private final RecordingEvents events = new RecordingEvents();
    private final Log log = new Log();

    private PurgeArchivedRentWarps purge(Duration delay) {
        RentConfig config = new RentConfig(
                true,
                BigDecimal.TEN,
                "default",
                Duration.ofDays(7),
                Duration.ofDays(3),
                delay,
                List.of(),
                Set.of("alex"),
                Set.of("staff"),
                Set.of("nether"));
        return new PurgeArchivedRentWarps(warps, purse, events, config, clock, log);
    }

    private static PlayerWarp warp(long id, PlayerRef owner, String name) {
        return PlayerWarp.create(
                        owner, owner.name(), PlayerWarpName.of(name), at(0, 64, 0), NOW.minus(Duration.ofDays(60)))
                .withId(PlayerWarpId.of(id));
    }

    /** A warp the rent sweep archived at {@code archivedAt}, once its grace deadline had passed. */
    private static PlayerWarp archivedForRent(PlayerWarp warp, Instant archivedAt) {
        RentState unpaid = new RentState(
                archivedAt.minus(Duration.ofDays(3)),
                Optional.of(archivedAt.minus(Duration.ofDays(3))),
                Optional.of(archivedAt));
        return warp.withRent(unpaid, archivedAt).withStatus(WarpStatus.ARCHIVED, archivedAt);
    }

    private static PlayerWarp archivedForRent(long id, PlayerRef owner, String name, Instant archivedAt) {
        return archivedForRent(warp(id, owner, name), archivedAt);
    }

    @Test
    void aWarpArchivedForRentIsDeletedForGoodOnceTheDelayHasPassed() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        warps.put(archivedForRent(1, SAM, "market", NOW));

        clock.advance(DELAY.minusSeconds(1));
        assertThat(purge.purgeDue(500)).isZero();
        assertThat(warps.findById(PlayerWarpId.of(1))).isPresent();

        clock.advance(Duration.ofSeconds(1));
        assertThat(purge.purgeDue(500)).isEqualTo(1);
        assertThat(warps.findById(PlayerWarpId.of(1))).isEmpty();
        assertThat(events.published()).containsExactly(new PlayerWarpDeleted(SAM, PlayerWarpName.of("market")));
        assertThat(log.infos()).singleElement().asString().contains("count=1").contains("warps=1:market");
    }

    @Test
    void aZeroDelayKeepsArchivedWarpsForever() {
        PurgeArchivedRentWarps purge = purge(Duration.ZERO);
        PlayerWarp market = archivedForRent(1, SAM, "market", NOW);
        warps.put(market);

        clock.advance(Duration.ofDays(3650));

        assertThat(purge.purgeDue(500)).isZero();
        assertThat(purge.purge(market)).isFalse();
        assertThat(warps.findById(PlayerWarpId.of(1))).isPresent();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void onlyAWarpArchivedForRentAndLeftAloneIsDeleted() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        warps.put(archivedForRent(1, SAM, "market", LONG_AGO));
        // Archived by its owner while paid up: no rent archive deadline.
        warps.put(warp(2, SAM, "home").withStatus(WarpStatus.ARCHIVED, LONG_AGO));
        // Restored by an admin after the rent archive, so it is live again.
        warps.put(archivedForRent(3, SAM, "farm", LONG_AGO).withStatus(WarpStatus.ACTIVE, LONG_AGO));
        // Restored and archived again recently: the delay counts from that last change.
        warps.put(archivedForRent(4, SAM, "shop", LONG_AGO).withStatus(WarpStatus.ARCHIVED, NOW));

        assertThat(purge.purgeDue(500)).isEqualTo(1);

        assertThat(warps.ids()).containsExactlyInAnyOrder(2L, 3L, 4L);
    }

    @Test
    void aWarpThatDoesNotQualifyIsNeverDeletedEvenWhenHandedInDirectly() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        List<PlayerWarp> ineligible = List.of(
                warp(2, SAM, "home").withStatus(WarpStatus.ARCHIVED, LONG_AGO),
                archivedForRent(3, SAM, "farm", LONG_AGO).withStatus(WarpStatus.ACTIVE, LONG_AGO),
                archivedForRent(4, SAM, "shop", LONG_AGO).withStatus(WarpStatus.ARCHIVED, NOW),
                archivedForRent(5, ALEX, "tower", LONG_AGO),
                archivedForRent(warp(6, SAM, "guild").withCategoryId(Optional.of("Staff"), LONG_AGO), LONG_AGO),
                archivedForRent(
                        PlayerWarp.create(
                                        SAM,
                                        "Sam",
                                        PlayerWarpName.of("fortress"),
                                        Position.of(NETHER, 0, 64, 0),
                                        NOW.minus(Duration.ofDays(60)))
                                .withId(PlayerWarpId.of(7)),
                        LONG_AGO));
        ineligible.forEach(warps::put);

        for (PlayerWarp warp : ineligible) {
            assertThat(purge.purge(warp)).as(warp.name().value()).isFalse();
        }

        assertThat(warps.ids()).containsExactlyInAnyOrder(2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void aWarpRestoredAfterThePageWasReadIsKept() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        warps.put(archivedForRent(1, SAM, "market", LONG_AGO));
        PlayerWarp read = purge.due(500).getFirst();

        warps.save(read.withStatus(WarpStatus.ACTIVE, NOW));

        assertThat(purge.purge(read)).isFalse();
        assertThat(warps.findById(PlayerWarpId.of(1)).map(PlayerWarp::status)).contains(WarpStatus.ACTIVE);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void aWarpAnotherBackendAlreadyDeletedIsNeitherPublishedNorCounted() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        warps.put(archivedForRent(1, SAM, "market", LONG_AGO));
        PlayerWarp read = purge.due(500).getFirst();

        warps.deleteById(PlayerWarpId.of(1));

        assertThat(purge.purge(read)).isFalse();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void earningsStillInTheBankArePaidToTheOwnerBeforeTheDelete() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        warps.put(archivedForRent(1, SAM, "market", LONG_AGO));
        warps.bank.put(PlayerWarpId.of(1), new BigDecimal("40"));

        assertThat(purge.purgeDue(500)).isEqualTo(1);

        assertThat(purse.wallet(SAM)).isEqualByComparingTo("40");
        assertThat(warps.findById(PlayerWarpId.of(1))).isEmpty();
    }

    @Test
    void aWarpWhoseEarningsCannotBePaidOutIsKeptWithItsBank() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        warps.put(archivedForRent(1, SAM, "market", LONG_AGO));
        warps.bank.put(PlayerWarpId.of(1), new BigDecimal("40"));
        purse.refuse = true;

        assertThat(purge.purgeDue(500)).isZero();

        assertThat(warps.findById(PlayerWarpId.of(1))).isPresent();
        assertThat(warps.bank.get(PlayerWarpId.of(1))).isEqualByComparingTo("40");
        assertThat(purse.wallet(SAM)).isEqualByComparingTo("0");
        assertThat(events.published()).isEmpty();
    }

    @Test
    void earningsThatCannotReachAnOfflineOwnerAreLeftAloneQuietly() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        warps.put(archivedForRent(1, SAM, "market", LONG_AGO));
        warps.bank.put(PlayerWarpId.of(1), new BigDecimal("40"));
        purse.onlineOnly.add(PlayerWarpId.of(1));

        assertThat(purge.purgeDue(500)).isZero();

        assertThat(purse.withdrawals).isZero();
        assertThat(warps.bank.get(PlayerWarpId.of(1))).isEqualByComparingTo("40");
        assertThat(warps.findById(PlayerWarpId.of(1))).isPresent();
        assertThat(log.errors()).isEmpty();
    }

    @Test
    void anEmptyBankNeedsNoPayoutWhateverTheCurrency() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        warps.put(archivedForRent(1, SAM, "market", LONG_AGO));
        purse.onlineOnly.add(PlayerWarpId.of(1));

        assertThat(purge.purgeDue(500)).isEqualTo(1);
    }

    @Test
    void warpsThatMustBeKeptDoNotBlockTheOnesBehindThem() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        for (long id = 1; id <= 2; id++) {
            warps.put(archivedForRent(id, SAM, "stuck" + id, LONG_AGO.minus(Duration.ofDays(3 - id))));
            warps.bank.put(PlayerWarpId.of(id), new BigDecimal("40"));
            purse.onlineOnly.add(PlayerWarpId.of(id));
        }
        warps.put(archivedForRent(3, SAM, "market", LONG_AGO));

        assertThat(purge.purgeDue(2)).isZero();
        assertThat(purge.purgeDue(2)).isEqualTo(1);

        assertThat(warps.ids()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void aWarpTransferredAfterThePageWasReadPaysItsNewOwner() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        PlayerRef kim = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000000c"), "Kim");
        warps.put(archivedForRent(1, SAM, "market", LONG_AGO));
        warps.bank.put(PlayerWarpId.of(1), new BigDecimal("40"));
        PlayerWarp read = purge.due(500).getFirst();

        warps.save(read.transferredTo(kim, "Kim", LONG_AGO));

        assertThat(purge.purge(read)).isTrue();
        assertThat(purse.wallet(kim)).isEqualByComparingTo("40");
        assertThat(purse.wallet(SAM)).isEqualByComparingTo("0");
        assertThat(events.published()).containsExactly(new PlayerWarpDeleted(kim, PlayerWarpName.of("market")));
    }

    @Test
    void oneWarpFailingIsLoggedAndTheRestOfThePassStillRuns() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        warps.put(archivedForRent(1, SAM, "market", LONG_AGO.minusSeconds(1)));
        warps.put(archivedForRent(2, SAM, "farm", LONG_AGO));
        warps.failingDeletes.add(PlayerWarpId.of(1));

        assertThat(purge.purgeDue(500)).isEqualTo(1);

        assertThat(warps.ids()).containsExactly(1L);
        assertThat(log.errors()).containsExactly("event=playerwarp_rent_hard_delete_failed warp=1");
        assertThat(log.infos()).singleElement().asString().contains("warps=2:farm");
    }

    @Test
    void aFailingPageReadIsLoggedAndEndsOnlyThisPass() {
        PurgeArchivedRentWarps purge = purge(DELAY);
        warps.put(archivedForRent(1, SAM, "market", LONG_AGO));
        warps.queryFails = true;

        assertThat(purge.purgeDue(500)).isZero();
        assertThat(log.errors()).containsExactly("event=playerwarp_rent_hard_delete_query_failed");

        warps.queryFails = false;
        assertThat(purge.purgeDue(500)).isEqualTo(1);
    }

    /** An in-memory store whose rent hard-delete query and guarded delete follow the port contract. */
    private static final class Warps implements PlayerWarpRepository {

        private final Map<PlayerWarpId, PlayerWarp> byId = new HashMap<>();
        final Map<PlayerWarpId, BigDecimal> bank = new HashMap<>();
        final Set<PlayerWarpId> failingDeletes = new HashSet<>();
        boolean queryFails;

        void put(PlayerWarp warp) {
            byId.put(warp.id().orElseThrow(), warp);
        }

        List<Long> ids() {
            return byId.keySet().stream().map(PlayerWarpId::value).toList();
        }

        private static boolean archivedForRentSince(PlayerWarp w, Instant cutoff) {
            return w.status() == WarpStatus.ARCHIVED
                    && !w.updatedAt().isAfter(cutoff)
                    && w.rent()
                            .flatMap(RentState::archiveAfter)
                            .map(deadline -> !deadline.isAfter(cutoff))
                            .orElse(false);
        }

        @Override
        public List<PlayerWarp> archivedForRent(Instant cutoff, Optional<RentArchiveCursor> after, int limit) {
            if (queryFails) {
                throw new IllegalStateException("database unavailable");
            }
            Comparator<PlayerWarp> order = Comparator.comparing((PlayerWarp w) ->
                            w.rent().flatMap(RentState::archiveAfter).orElseThrow())
                    .thenComparingLong(w -> w.id().orElseThrow().value());
            return byId.values().stream()
                    .filter(w -> archivedForRentSince(w, cutoff))
                    .filter(w -> after.map(cursor -> isAfter(w, cursor)).orElse(true))
                    .sorted(order)
                    .limit(limit)
                    .toList();
        }

        private static boolean isAfter(PlayerWarp w, RentArchiveCursor cursor) {
            Instant deadline = w.rent().flatMap(RentState::archiveAfter).orElseThrow();
            return deadline.isAfter(cursor.archiveAfter())
                    || (deadline.equals(cursor.archiveAfter())
                            && w.id().orElseThrow().value() > cursor.id().value());
        }

        @Override
        public boolean deleteIfArchivedForRent(PlayerWarpId id, Instant cutoff) {
            if (failingDeletes.contains(id)) {
                throw new IllegalStateException("delete failed");
            }
            PlayerWarp stored = byId.get(id);
            if (stored == null
                    || !archivedForRentSince(stored, cutoff)
                    || bank.getOrDefault(id, BigDecimal.ZERO).signum() > 0) {
                return false;
            }
            byId.remove(id);
            bank.remove(id);
            return true;
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

    /** Owners' wallets; a withdraw empties the warp's bank into its owner's wallet unless the credit is refused. */
    private static final class Purse implements PlayerWarpEconomy {

        private final Warps warps;
        private final Map<PlayerRef, BigDecimal> wallets = new HashMap<>();
        boolean refuse;
        final Set<PlayerWarpId> onlineOnly = new HashSet<>();
        int withdrawals;

        Purse(Warps warps) {
            this.warps = warps;
        }

        BigDecimal wallet(PlayerRef who) {
            return wallets.getOrDefault(who, BigDecimal.ZERO);
        }

        @Override
        public boolean canPayOutOffline(PlayerWarpId warp) {
            return warps.bank.getOrDefault(warp, BigDecimal.ZERO).signum() <= 0 || !onlineOnly.contains(warp);
        }

        @Override
        public Result<Unit, ChargeError> withdraw(PlayerWarpId warp, PlayerRef to) {
            withdrawals++;
            BigDecimal held = warps.bank.getOrDefault(warp, BigDecimal.ZERO);
            if (held.signum() <= 0) {
                return Result.ok();
            }
            if (refuse) {
                return Result.err(ChargeError.PROVIDER_ERROR);
            }
            warps.bank.put(warp, BigDecimal.ZERO);
            wallets.merge(to, held, BigDecimal::add);
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> charge(PlayerRef payer, BigDecimal price, String currencyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, ChargeError> accrue(PlayerWarpId warp, BigDecimal price, String currencyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, ChargeError> collectRent(
                PlayerWarpId warp, PlayerRef owner, BigDecimal amount, String currencyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, ChargeError> refund(PlayerRef to, BigDecimal amount, String currencyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, ChargeError> chargeOwner(PlayerRef owner, BigDecimal amount, String currencyId) {
            throw new UnsupportedOperationException();
        }
    }

    /** Keeps each info line with its arguments filled in, and the message of each error. */
    private static final class Log implements Logger {

        private final List<String> infos = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        List<String> infos() {
            return List.copyOf(infos);
        }

        List<String> errors() {
            return List.copyOf(errors);
        }

        @Override
        public void info(String message, Object... args) {
            String line = message;
            for (Object arg : args) {
                line = line.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
            }
            infos.add(line);
        }

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}

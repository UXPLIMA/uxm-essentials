package com.uxplima.uxmessentials.ranks.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.testing.ManualScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import org.junit.jupiter.api.Test;

class CachedPlayerRankRepositoryTest {

    private static final UUID PLAYER = ALEX.uuid();
    private static final PlayerRank GOLD = new PlayerRank(RankId.of("gold"), new Prestige(2));
    private static final Duration WAIT = Duration.ofSeconds(5);
    // Nothing runs the queued read while the pre-login waits, so this is how long the timeout test blocks.
    private static final Duration NO_WAIT = Duration.ofMillis(1);

    private final ManualScheduler scheduler = new ManualScheduler();
    private final InMemoryPlayerRanks rows = new InMemoryPlayerRanks();
    private final CachedPlayerRankRepository cache = new CachedPlayerRankRepository(rows, scheduler, new QuietLog());

    @Test
    void aJoinLoadsOffTheTickAndLaterReadsComeFromMemory() {
        rows.put(PLAYER, "gold", 2);

        cache.join(PLAYER);
        assertThat(cache.cached(PLAYER)).isEmpty();
        assertThat(rows.reads()).isZero();

        scheduler.runAsync();
        for (int refresh = 0; refresh < 20; refresh++) {
            assertThat(cache.cached(PLAYER)).contains(Optional.of(GOLD));
        }
        assertThat(rows.reads()).isEqualTo(1);
    }

    @Test
    void aPlayerWithNoStoredRankIsAHitOnceLoaded() {
        cache.join(PLAYER);
        scheduler.runAsync();

        assertThat(cache.cached(PLAYER)).contains(Optional.empty());
        assertThat(scheduler.pendingAsync()).isZero();
    }

    @Test
    void aMissForAnOnlinePlayerLoadsInTheBackground() {
        rows.put(PLAYER, "gold", 2);
        rows.failReads(true);
        cache.join(PLAYER);
        scheduler.runAsync();
        rows.failReads(false);

        assertThat(cache.cached(PLAYER)).isEmpty();
        scheduler.runAsync();

        assertThat(cache.cached(PLAYER)).contains(Optional.of(GOLD));
    }

    @Test
    void repeatedMissesQueueOneLoad() {
        cache.join(PLAYER);
        cache.cached(PLAYER);
        cache.cached(PLAYER);

        assertThat(scheduler.pendingAsync()).isEqualTo(1);
    }

    @Test
    void anOfflinePlayerIsNeverLoadedFromATickSafeRead() {
        rows.put(PLAYER, "gold", 2);

        assertThat(cache.cached(PLAYER)).isEmpty();
        assertThat(scheduler.pendingAsync()).isZero();
        assertThat(rows.reads()).isZero();
    }

    @Test
    void aSaveWritesThroughAndIsServedWithoutAnotherRead() {
        cache.join(PLAYER);
        scheduler.runAsync();

        cache.save(PLAYER, RankId.of("gold"), new Prestige(2));

        assertThat(rows.row(PLAYER)).contains(GOLD);
        assertThat(cache.cached(PLAYER)).contains(Optional.of(GOLD));
        assertThat(rows.reads()).isEqualTo(1);
    }

    @Test
    void aFailedSaveLeavesWhatTheDatabaseStillHolds() {
        rows.put(PLAYER, "gold", 2);
        cache.join(PLAYER);
        scheduler.runAsync();
        rows.failSaves(true);

        assertThatThrownBy(() -> cache.save(PLAYER, RankId.of("diamond"), new Prestige(2)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(cache.cached(PLAYER)).contains(Optional.of(GOLD));
    }

    @Test
    void aSaveDuringTheJoinLoadIsNotOverwrittenByTheOlderRead() {
        rows.put(PLAYER, "gold", 2);
        cache.join(PLAYER);
        PlayerRank diamond = new PlayerRank(RankId.of("diamond"), new Prestige(2));

        cache.save(PLAYER, diamond.rankId(), diamond.prestige());
        rows.put(PLAYER, "gold", 2);
        scheduler.runAsync();

        assertThat(cache.cached(PLAYER)).contains(Optional.of(diamond));
    }

    @Test
    void aQuitForgetsThePlayer() {
        rows.put(PLAYER, "gold", 2);
        cache.join(PLAYER);
        scheduler.runAsync();

        cache.quit(PLAYER);

        assertThat(cache.cached(PLAYER)).isEmpty();
        assertThat(scheduler.pendingAsync()).isZero();
    }

    @Test
    void aLoadThatFinishesAfterTheQuitIsDropped() {
        rows.put(PLAYER, "gold", 2);
        cache.join(PLAYER);
        cache.quit(PLAYER);

        scheduler.runAsync();

        assertThat(cache.cached(PLAYER)).isEmpty();
    }

    @Test
    void aChangeOnAnotherServerIsReloadedForAnOnlinePlayer() {
        rows.put(PLAYER, "silver", 1);
        cache.join(PLAYER);
        scheduler.runAsync();
        rows.put(PLAYER, "gold", 2);

        cache.refresh(PLAYER);
        assertThat(cache.cached(PLAYER)).contains(Optional.of(new PlayerRank(RankId.of("silver"), new Prestige(1))));
        scheduler.runAsync();

        assertThat(cache.cached(PLAYER)).contains(Optional.of(GOLD));
    }

    @Test
    void aPreLoginLoadIsServedAtOnceAndTheJoinReadsNothingMore() {
        rows.put(PLAYER, "gold", 2);
        scheduler.runAsyncInline();

        cache.preload(PLAYER, WAIT);
        cache.join(PLAYER);

        assertThat(cache.cached(PLAYER)).contains(Optional.of(GOLD));
        assertThat(scheduler.pendingAsync()).isZero();
        assertThat(rows.reads()).isEqualTo(1);
    }

    @Test
    void aJoinFetchesAgainWhenThePreLoginReadFailed() {
        rows.put(PLAYER, "gold", 2);
        rows.failReads(true);
        cache.preload(PLAYER, NO_WAIT);
        scheduler.runAsync();
        rows.failReads(false);

        cache.join(PLAYER);
        scheduler.runAsync();

        assertThat(cache.cached(PLAYER)).contains(Optional.of(GOLD));
    }

    @Test
    void aSlowPreLoginReadLetsTheLoginGoOnAndLandsLater() {
        rows.put(PLAYER, "gold", 2);

        cache.preload(PLAYER, NO_WAIT);
        assertThat(cache.cached(PLAYER)).isEmpty();
        cache.join(PLAYER);
        scheduler.runAsync();

        assertThat(cache.cached(PLAYER)).contains(Optional.of(GOLD));
        assertThat(rows.reads()).isEqualTo(1);
    }

    @Test
    void aConnectionClosedBeforeJoiningLeavesNothingBehind() {
        rows.put(PLAYER, "gold", 2);
        scheduler.runAsyncInline();
        cache.preload(PLAYER, WAIT);

        cache.quit(PLAYER);

        assertThat(cache.cached(PLAYER)).isEmpty();
    }

    @Test
    void aSlowPreLoginReadThatLandsAfterTheConnectionClosedIsDropped() {
        rows.put(PLAYER, "gold", 2);
        cache.preload(PLAYER, NO_WAIT);

        cache.quit(PLAYER);
        scheduler.runAsync();

        assertThat(cache.cached(PLAYER)).isEmpty();
        assertThat(scheduler.pendingAsync()).isZero();
    }

    @Test
    void aLoadStartedBeforeARemoteRefreshCannotLandAfterIt() {
        rows.put(PLAYER, "silver", 1);
        cache.join(PLAYER);
        cache.refresh(PLAYER);

        // The refresh read runs first and sees the peer's new row; the older join read then answers the old one.
        rows.answerNextReadWith(Optional.of(GOLD));
        scheduler.runAsyncNewestFirst();

        assertThat(cache.cached(PLAYER)).contains(Optional.of(GOLD));
    }

    @Test
    void aRemoteRefreshThatALocalSaveOvertookDoesNotRestoreTheOldRank() {
        rows.put(PLAYER, "silver", 1);
        cache.join(PLAYER);
        scheduler.runAsync();
        PlayerRank silver = new PlayerRank(RankId.of("silver"), new Prestige(1));

        cache.refresh(PLAYER);
        cache.save(PLAYER, GOLD.rankId(), GOLD.prestige());
        rows.answerNextReadWith(Optional.of(silver));
        scheduler.runAsync();

        assertThat(cache.cached(PLAYER)).contains(Optional.of(GOLD));
    }

    @Test
    void aLoadFromBeforeAQuitAndRejoinIsDropped() {
        rows.put(PLAYER, "gold", 2);
        cache.join(PLAYER);
        cache.quit(PLAYER);
        cache.join(PLAYER);
        rows.answerNextReadWith(Optional.of(new PlayerRank(RankId.of("silver"), new Prestige(1))));

        scheduler.runAsync();
        assertThat(cache.cached(PLAYER)).isEmpty();
        scheduler.runAsync();

        assertThat(cache.cached(PLAYER)).contains(Optional.of(GOLD));
    }

    @Test
    void theBlockingReadFallsBackToTheDatabaseForAnOfflinePlayer() {
        rows.put(PLAYER, "gold", 2);

        assertThat(cache.find(PLAYER)).contains(GOLD);
        assertThat(cache.cached(PLAYER)).isEmpty();
    }
}

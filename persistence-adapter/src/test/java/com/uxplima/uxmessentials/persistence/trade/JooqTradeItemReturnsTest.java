package com.uxplima.uxmessentials.persistence.trade;

import static com.uxplima.uxmessentials.persistence.jooq.tables.TradeItemReturns.TRADE_ITEM_RETURNS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.trade.application.port.TradeItemReturns;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/**
 * Kept trade items come back byte for byte, and a batch is handed out under one claim at a time: a second server
 * cannot take it while the first holds it, and only the holder can remove it. A claim nobody finishes goes stale and
 * can be taken again, so a crash between the claim and the hand-over loses nothing.
 */
class JooqTradeItemReturnsTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final Duration STALE = Duration.ofMinutes(10);
    private static final VaultContents SWORD = VaultContents.of(new byte[] {1, 2, 3, -128, 127});

    private final MovingClock clock = new MovingClock();
    private final List<String> errors = new ArrayList<>();

    @TempDir
    Path folder;

    @Test
    void keptItemsComeBackUnchangedOldestFirstAndOnlyForTheirOwner() {
        DSLContext dsl = openDsl();
        VaultContents shulker = VaultContents.of(new byte[] {9, 8, 7});
        TradeItemReturns returns = store(dsl);
        returns.keep(ALEX, SWORD);
        clock.advance(Duration.ofSeconds(1));
        returns.keep(ALEX, shulker);
        returns.keep(SAM, SWORD);

        assertThat(returns.claimable(ALEX))
                .extracting(TradeItemReturns.Kept::items)
                .containsExactly(SWORD, shulker);
    }

    @Test
    void aBatchIsClaimedByOneServerAndRemovedOnlyByThatClaim() {
        DSLContext dsl = openDsl();
        TradeItemReturns lobby = store(dsl);
        TradeItemReturns survival = store(dsl);
        lobby.keep(ALEX, SWORD);
        String id = lobby.claimable(ALEX).get(0).id();

        boolean lobbyClaimed = lobby.claim(id, "lobby");
        boolean survivalClaimed = survival.claim(id, "survival");

        assertThat(lobbyClaimed).isTrue();
        assertThat(survivalClaimed).isFalse();
        assertThat(survival.claimable(ALEX)).isEmpty();
        assertThat(survival.finish(id, "survival", VaultContents.empty())).isFalse();
        assertThat(lobby.finish(id, "lobby", VaultContents.empty())).isTrue();
        assertThat(rows(dsl)).isZero();
    }

    @Test
    void aReleasedClaimLeavesTheBatchWholeForTheNextJoin() {
        DSLContext dsl = openDsl();
        TradeItemReturns returns = store(dsl);
        returns.keep(ALEX, SWORD);
        String id = returns.claimable(ALEX).get(0).id();
        returns.claim(id, "lobby");

        returns.release(id, "lobby");

        assertThat(returns.claimable(ALEX))
                .extracting(TradeItemReturns.Kept::items)
                .containsExactly(SWORD);
        assertThat(returns.claim(id, "survival")).isTrue();
    }

    @Test
    void whatDidNotFitReplacesTheBatchAndIsFreeToClaimAgain() {
        DSLContext dsl = openDsl();
        TradeItemReturns returns = store(dsl);
        returns.keep(ALEX, SWORD);
        String id = returns.claimable(ALEX).get(0).id();
        returns.claim(id, "lobby");
        VaultContents rest = VaultContents.of(new byte[] {127});

        assertThat(returns.finish(id, "lobby", rest)).isTrue();

        assertThat(returns.claimable(ALEX))
                .extracting(TradeItemReturns.Kept::items)
                .containsExactly(rest);
    }

    @Test
    void aClaimNobodyFinishedCanBeTakenAgainOnceItIsStale() {
        DSLContext dsl = openDsl();
        TradeItemReturns returns = store(dsl);
        returns.keep(ALEX, SWORD);
        String id = returns.claimable(ALEX).get(0).id();
        returns.claim(id, "crashed");

        clock.advance(STALE.minusSeconds(1));
        assertThat(returns.claimable(ALEX)).isEmpty();
        assertThat(returns.claim(id, "lobby")).isFalse();

        clock.advance(Duration.ofSeconds(2));
        assertThat(returns.claimable(ALEX))
                .extracting(TradeItemReturns.Kept::items)
                .containsExactly(SWORD);
        assertThat(returns.claim(id, "lobby")).isTrue();
        assertThat(returns.finish(id, "crashed", VaultContents.empty())).isFalse();
        assertThat(returns.finish(id, "lobby", VaultContents.empty())).isTrue();
    }

    @Test
    void aRowThatCannotBeReadIsSkippedAndKeptWhileTheRestAreHandedOut() {
        DSLContext dsl = openDsl();
        TradeItemReturns returns = store(dsl);
        dsl.insertInto(TRADE_ITEM_RETURNS)
                .set(TRADE_ITEM_RETURNS.ID, "broken")
                .set(TRADE_ITEM_RETURNS.OWNER, ALEX.toString())
                .set(TRADE_ITEM_RETURNS.CONTENTS, "not base64 !")
                .set(TRADE_ITEM_RETURNS.CREATED_AT, NOW.toEpochMilli() - 1)
                .execute();
        returns.keep(ALEX, SWORD);

        assertThat(returns.claimable(ALEX))
                .extracting(TradeItemReturns.Kept::items)
                .containsExactly(SWORD);
        assertThat(rows(dsl)).isEqualTo(2);
        assertThat(errors).singleElement().asString().contains("id=broken");
    }

    @Test
    void aBatchFarLargerThanAMysqlTextColumnComesBackWhole() {
        DSLContext dsl = openDsl();
        byte[] bytes = new byte[1 << 20];
        new Random(7).nextBytes(bytes);
        VaultContents shulkers = VaultContents.of(bytes);

        store(dsl).keep(ALEX, shulkers);

        assertThat(store(dsl).claimable(ALEX))
                .extracting(TradeItemReturns.Kept::items)
                .containsExactly(shulkers);
    }

    @Test
    void nothingIsKeptForAnEmptyBatch() {
        DSLContext dsl = openDsl();

        store(dsl).keep(ALEX, VaultContents.empty());

        assertThat(rows(dsl)).isZero();
    }

    private TradeItemReturns store(DSLContext dsl) {
        return new JooqTradeItemReturns(dsl, clock, STALE, new Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {
                errors.add(message);
            }

            @Override
            public void debug(String message, Object... args) {}
        });
    }

    private static int rows(DSLContext dsl) {
        return dsl.fetchCount(TRADE_ITEM_RETURNS);
    }

    private DSLContext openDsl() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        return DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
    }

    /** A clock the test moves by hand. */
    private static final class MovingClock extends Clock {

        private Instant now = NOW;

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

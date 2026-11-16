package com.uxplima.uxmessentials.persistence.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.trade.application.port.TradeItemReturns;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/**
 * Kept trade items come back byte for byte, oldest first, and a batch can be claimed once: a second server that read
 * the same row cannot hand it out again.
 */
class JooqTradeItemReturnsTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @TempDir
    Path folder;

    @Test
    void keptItemsComeBackUnchangedOldestFirstAndOnlyForTheirOwner() {
        DSLContext dsl = openDsl();
        VaultContents sword = VaultContents.of(new byte[] {1, 2, 3, -128, 127});
        VaultContents shulker = VaultContents.of(new byte[] {9, 8, 7});
        new JooqTradeItemReturns(dsl, Clock.fixed(NOW, ZoneOffset.UTC)).keep(ALEX, sword);
        TradeItemReturns returns = new JooqTradeItemReturns(dsl, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
        returns.keep(ALEX, shulker);
        returns.keep(SAM, sword);

        assertThat(returns.kept(ALEX)).extracting(TradeItemReturns.Kept::items).containsExactly(sword, shulker);
    }

    @Test
    void aBatchIsClaimedOnceEvenWhenTwoServersReadIt() {
        DSLContext dsl = openDsl();
        TradeItemReturns lobby = new JooqTradeItemReturns(dsl, Clock.fixed(NOW, ZoneOffset.UTC));
        TradeItemReturns survival = new JooqTradeItemReturns(dsl, Clock.fixed(NOW, ZoneOffset.UTC));
        lobby.keep(ALEX, VaultContents.of(new byte[] {1}));
        List<TradeItemReturns.Kept> seenByLobby = lobby.kept(ALEX);
        List<TradeItemReturns.Kept> seenBySurvival = survival.kept(ALEX);

        boolean lobbyClaimed = lobby.claim(seenByLobby.get(0).id());
        boolean survivalClaimed = survival.claim(seenBySurvival.get(0).id());

        assertThat(lobbyClaimed).isTrue();
        assertThat(survivalClaimed).isFalse();
        assertThat(lobby.kept(ALEX)).isEmpty();
    }

    @Test
    void nothingIsKeptForAnEmptyBatch() {
        TradeItemReturns returns = new JooqTradeItemReturns(openDsl(), Clock.fixed(NOW, ZoneOffset.UTC));

        returns.keep(ALEX, VaultContents.empty());

        assertThat(returns.kept(ALEX)).isEmpty();
    }

    private DSLContext openDsl() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        return DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
    }
}

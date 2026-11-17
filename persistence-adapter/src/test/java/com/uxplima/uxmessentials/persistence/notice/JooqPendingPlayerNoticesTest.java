package com.uxplima.uxmessentials.persistence.notice;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerNotices.PLAYER_NOTICES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.application.message.TrustedMarkup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PendingPlayerNotices;
import com.uxplima.uxmessentials.shared.domain.PlayerNotice;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import org.jooq.DSLContext;
import org.jooq.Delete;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kept lines come back with their key and every value intact, in the order they were kept, only for their recipient,
 * and are handed out once: a second server taking after the first gets nothing. A take the database fails leaves every
 * line for the next one. A player holds only so many lines, the newest, and old lines are dropped.
 */
@EveryBackend
class JooqPendingPlayerNoticesTest {

    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID KIM = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final Instant FIRST = Instant.parse("2030-05-01T12:00:00Z");
    private static final int KEPT = 50;

    private DSLContext dsl;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
    }

    @Test
    void keptLinesComeBackWholeInOrderAndOnlyForTheirRecipient() {
        PendingPlayerNotices store = new JooqPendingPlayerNotices(dsl, new Quiet(), KEPT);
        PlayerNotice paid = notice(SAM, Map.of("player", "Alex", "amount", "$1,000.50 & change=yes"), FIRST);
        PlayerNotice later = notice(SAM, Map.of(), FIRST.plusSeconds(60));
        PlayerNotice kims = notice(KIM, Map.of("player", "Ünal"), FIRST);
        store.keep(later);
        store.keep(kims);
        store.keep(paid);

        assertThat(store.take(SAM)).satisfiesExactly(first -> same(first, paid), second -> same(second, later));
        assertThat(store.take(KIM)).singleElement().satisfies(only -> same(only, kims));
    }

    @Test
    void aLineIsHandedOutOnceAcrossServers() {
        PendingPlayerNotices lobby = new JooqPendingPlayerNotices(dsl, new Quiet(), KEPT);
        PendingPlayerNotices survival = new JooqPendingPlayerNotices(dsl, new Quiet(), KEPT);
        lobby.keep(notice(SAM, Map.of("player", "Alex"), FIRST));

        assertThat(survival.take(SAM)).hasSize(1);
        assertThat(lobby.take(SAM)).isEmpty();
    }

    @Test
    void aTakeTheDatabaseFailsLeavesEveryLineForTheNextOne() {
        PendingPlayerNotices store = new JooqPendingPlayerNotices(dsl, new Quiet(), KEPT);
        store.keep(notice(SAM, Map.of("amount", "1"), FIRST));
        store.keep(notice(SAM, Map.of("amount", "2"), FIRST.plusSeconds(1)));
        store.keep(notice(SAM, Map.of("amount", "3"), FIRST.plusSeconds(2)));
        PendingPlayerNotices failing = new JooqPendingPlayerNotices(failingOnDelete(1), new Quiet(), KEPT);

        assertThatThrownBy(() -> failing.take(SAM)).isInstanceOf(PersistenceException.class);

        assertThat(store.take(SAM))
                .extracting(kept -> kept.line().args().get("amount"))
                .containsExactly("1", "2", "3");
    }

    @Test
    void aTakeThatLosesARaceReadsAgainAndHandsOutTheLines() {
        PendingPlayerNotices store = new JooqPendingPlayerNotices(dsl, new Quiet(), KEPT);
        store.keep(notice(SAM, Map.of("amount", "1"), FIRST));
        store.keep(notice(SAM, Map.of("amount", "2"), FIRST.plusSeconds(1)));
        PendingPlayerNotices racing = new JooqPendingPlayerNotices(losingDeletes(1), new Quiet(), KEPT);

        assertThat(racing.take(SAM))
                .extracting(kept -> kept.line().args().get("amount"))
                .containsExactly("1", "2");
        assertThat(store.take(SAM)).isEmpty();
    }

    @Test
    void aTakeThatKeepsLosingGivesUpAndLeavesTheLinesForTheNextOne() {
        PendingPlayerNotices store = new JooqPendingPlayerNotices(dsl, new Quiet(), KEPT);
        store.keep(notice(SAM, Map.of("amount", "1"), FIRST));
        PendingPlayerNotices racing = new JooqPendingPlayerNotices(losingDeletes(Integer.MAX_VALUE), new Quiet(), KEPT);

        assertThat(racing.take(SAM)).isEmpty();

        assertThat(store.take(SAM)).hasSize(1);
    }

    @Test
    void aPlayerSentMoreLinesThanAreKeptHoldsTheNewest() {
        PendingPlayerNotices store = new JooqPendingPlayerNotices(dsl, new Quiet(), 3);
        for (int i = 1; i <= 200; i++) {
            store.keep(notice(SAM, Map.of("amount", Integer.toString(i)), FIRST.plusSeconds(i)));
        }
        store.keep(notice(KIM, Map.of("amount", "kim"), FIRST));

        assertThat(store.take(SAM))
                .extracting(kept -> kept.line().args().get("amount"))
                .containsExactly("198", "199", "200");
        assertThat(store.take(KIM)).hasSize(1);
    }

    @Test
    void aValueMarkedAsMarkupIsStoredWithoutTheMark() {
        PendingPlayerNotices store = new JooqPendingPlayerNotices(dsl, new Quiet(), KEPT);
        store.keep(notice(SAM, Map.of("amount", TrustedMarkup.of("<gold>$5</gold>")), FIRST));

        String stored = dsl.select(PLAYER_NOTICES.ARGS).from(PLAYER_NOTICES).fetchOne(PLAYER_NOTICES.ARGS);

        // The mark only counts on the server run that made it, so it is never written down.
        assertThat(stored).doesNotContain("%EF%B7%91").doesNotContain("\uFDD1");
        assertThat(store.take(SAM))
                .extracting(kept -> kept.line().args().get("amount"))
                .containsExactly("<gold>$5</gold>");
    }

    @Test
    void linesOlderThanTheCutoffAreDroppedAndNewerOnesKept() {
        PendingPlayerNotices store = new JooqPendingPlayerNotices(dsl, new Quiet(), KEPT);
        store.keep(notice(SAM, Map.of("amount", "old"), FIRST));
        store.keep(notice(KIM, Map.of("amount", "old"), FIRST.plusSeconds(10)));
        store.keep(notice(SAM, Map.of("amount", "new"), FIRST.plusSeconds(3600)));

        assertThat(store.purgeOlderThan(FIRST.plusSeconds(60))).isEqualTo(2);

        assertThat(store.take(SAM))
                .extracting(kept -> kept.line().args().get("amount"))
                .containsExactly("new");
        assertThat(store.take(KIM)).isEmpty();
    }

    /** The shared database, failing the {@code nth} delete it is asked to run, as a deadlock or a pool timeout does. */
    private DSLContext failingOnDelete(int nth) {
        AtomicInteger deletes = new AtomicInteger();
        ExecuteListener failure = new ExecuteListener() {
            @Override
            public void executeStart(ExecuteContext ctx) {
                if (ctx.query() instanceof Delete<?> && deletes.incrementAndGet() == nth) {
                    throw new DataAccessException("deadlock found when trying to get lock");
                }
            }
        };
        return dsl.configuration().deriveAppending(failure).dsl();
    }

    /**
     * The shared database, where the first {@code times} deletes find their rows already gone, as when another server
     * took them in between this take's read and its delete.
     */
    private DSLContext losingDeletes(int times) {
        AtomicInteger deletes = new AtomicInteger();
        ExecuteListener race = new ExecuteListener() {
            // The statement is prepared from the rendered text, so the text is changed as it is rendered.
            @Override
            public void renderEnd(ExecuteContext ctx) {
                String sql = ctx.sql();
                if (ctx.query() instanceof Delete<?> && sql != null && deletes.incrementAndGet() <= times) {
                    ctx.sql(sql + " AND 1 = 0");
                }
            }
        };
        return dsl.configuration().deriveAppending(race).dsl();
    }

    private static PlayerNotice notice(UUID recipient, Map<String, String> args, Instant at) {
        return new PlayerNotice(recipient, new MessageLine(() -> "economy.pay-received", args), at);
    }

    /** The stored key is read back as a key of its own, so lines are compared by what they hold. */
    private static void same(PlayerNotice actual, PlayerNotice expected) {
        assertThat(actual.recipient()).isEqualTo(expected.recipient());
        assertThat(actual.line().key().key()).isEqualTo(expected.line().key().key());
        assertThat(actual.line().args()).isEqualTo(expected.line().args());
        assertThat(actual.at()).isEqualTo(expected.at());
    }

    private static final class Quiet implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

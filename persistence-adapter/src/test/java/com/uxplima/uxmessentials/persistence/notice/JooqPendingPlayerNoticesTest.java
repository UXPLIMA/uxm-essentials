package com.uxplima.uxmessentials.persistence.notice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
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
 * and are handed out once: a second server taking after the first gets nothing. A take the database fails part way
 * through still hands back the lines it already removed.
 */
@EveryBackend
class JooqPendingPlayerNoticesTest {

    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID KIM = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final Instant FIRST = Instant.parse("2030-05-01T12:00:00Z");

    private DSLContext dsl;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
    }

    @Test
    void keptLinesComeBackWholeInOrderAndOnlyForTheirRecipient() {
        PendingPlayerNotices store = new JooqPendingPlayerNotices(dsl, new Quiet());
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
        PendingPlayerNotices lobby = new JooqPendingPlayerNotices(dsl, new Quiet());
        PendingPlayerNotices survival = new JooqPendingPlayerNotices(dsl, new Quiet());
        lobby.keep(notice(SAM, Map.of("player", "Alex"), FIRST));

        assertThat(survival.take(SAM)).hasSize(1);
        assertThat(lobby.take(SAM)).isEmpty();
    }

    @Test
    void aTakeThatFailsPartWayHandsBackTheLinesItRemovedAndKeepsTheRest() {
        PendingPlayerNotices store = new JooqPendingPlayerNotices(dsl, new Quiet());
        store.keep(notice(SAM, Map.of("amount", "1"), FIRST));
        store.keep(notice(SAM, Map.of("amount", "2"), FIRST.plusSeconds(1)));
        store.keep(notice(SAM, Map.of("amount", "3"), FIRST.plusSeconds(2)));
        Errors errors = new Errors();
        PendingPlayerNotices failing = new JooqPendingPlayerNotices(failingOnDelete(2), errors);

        assertThat(failing.take(SAM))
                .as("the first line is gone from the table, so it must be shown")
                .extracting(kept -> kept.line().args().get("amount"))
                .containsExactly("1");
        assertThat(errors.lines).hasSize(1);
        assertThat(store.take(SAM))
                .as("the lines the failed take left are handed out by the next one")
                .extracting(kept -> kept.line().args().get("amount"))
                .containsExactly("2", "3");
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

    /** Keeps the error lines, which are what an operator would be told. */
    private static final class Errors implements Logger {

        private final List<String> lines = new CopyOnWriteArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            lines.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
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

package com.uxplima.uxmessentials.persistence.notice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PendingPlayerNotices;
import com.uxplima.uxmessentials.shared.domain.PlayerNotice;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kept lines come back with their key and every value intact, in the order they were kept, only for their recipient,
 * and are handed out once: a second server taking after the first gets nothing.
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

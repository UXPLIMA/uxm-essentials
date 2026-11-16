package com.uxplima.uxmessentials.persistence.messaging;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Mail.MAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToLongFunction;

import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

/**
 * Two writers can read the same {@code max(id)} before either commits. The one that commits second must still store
 * its mail, under a key of its own, rather than lose it to the primary key.
 */
@EveryBackend
class JooqMailRepositoryTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a2"), "Sam");
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void aMailWhoseKeyAnotherWriterTookFirstIsStoredUnderTheNextKey(TestDatabase database) {
        DSLContext dsl = database.dsl();
        MailItem first = new JooqMailRepository(dsl).append(mail("first", NOW));
        // This writer read the maximum before the first one committed, so the key it read is already taken.
        JooqMailRepository late =
                new JooqMailRepository(dsl, staleOnce(first.id().value()));

        MailItem second = late.append(mail("second", NOW.plusSeconds(1)));

        assertThat(second.id()).isEqualTo(MailId.of(first.id().value() + 1));
        assertThat(late.load(SAM).items())
                .extracting(item -> item.body().value())
                .containsExactly("second", "first");
    }

    @Test
    void aKeyThatStaysTakenFailsAfterTheLastAttemptAndStoresNothing(TestDatabase database) {
        DSLContext dsl = database.dsl();
        MailItem first = new JooqMailRepository(dsl).append(mail("first", NOW));
        AtomicInteger reads = new AtomicInteger();
        JooqMailRepository alwaysStale = new JooqMailRepository(dsl, ignored -> {
            reads.incrementAndGet();
            return first.id().value();
        });

        assertThatThrownBy(() -> alwaysStale.append(mail("second", NOW.plusSeconds(1))))
                .isInstanceOf(PersistenceException.class);
        assertThat(reads).hasValue(JooqMailRepository.APPEND_ATTEMPTS);
        assertThat(alwaysStale.load(SAM).items())
                .extracting(item -> item.body().value())
                .containsExactly("first");
    }

    @Test
    void aFailureThatIsNotAKeyClashIsNotTriedAgain(TestDatabase database) {
        DSLContext dsl = database.dsl();
        AtomicInteger reads = new AtomicInteger();
        JooqMailRepository broken = new JooqMailRepository(dsl, ignored -> {
            reads.incrementAndGet();
            return dsl.select(DSL.field("no_such_column")).from(MAIL).fetchOne(0, Long.class);
        });

        assertThatThrownBy(() -> broken.append(mail("lost", NOW))).isInstanceOf(PersistenceException.class);
        assertThat(reads).hasValue(1);
    }

    /** A key read that hands out {@code taken} once, as a writer that lost the race would, then reads truly. */
    private static ToLongFunction<DSLContext> staleOnce(long taken) {
        AtomicInteger calls = new AtomicInteger();
        return dsl -> {
            if (calls.getAndIncrement() == 0) {
                return taken;
            }
            Long max = dsl.select(DSL.max(MAIL.ID)).from(MAIL).fetchOne(0, Long.class);
            return (max == null ? 0L : max) + 1L;
        };
    }

    private static MailItem mail(String body, Instant sentAt) {
        return MailItem.compose(SAM, MailSender.player(ALEX), MessageBody.of(body), sentAt);
    }
}

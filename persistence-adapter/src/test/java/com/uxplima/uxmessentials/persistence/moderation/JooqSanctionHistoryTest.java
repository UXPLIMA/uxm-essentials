package com.uxplima.uxmessentials.persistence.moderation;

import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationSanctionHistory.MODERATION_SANCTION_HISTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToLongFunction;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A history row is written whole even when its reason is longer than the column: the sanction it records is already
 * stored, so the reason is fitted to the column's width rather than failing the write on a backend that enforces it.
 */
@EveryBackend
class JooqSanctionHistoryTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Alex");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID ROBIN = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID NO_PLAYER = new UUID(0L, 0L);
    private static final PlayerRef BEA = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a4"), "Bea");
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private DSLContext dsl;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
    }

    @Test
    void aReasonLongerThanTheColumnIsStoredFittedToIt() {
        SanctionHistory history = openStore();
        String reason = "reached 3 warnings, the last for: " + "x".repeat(240);

        history.append(new SanctionHistoryEntry(
                SanctionAction.MUTE,
                SAM,
                Issuer.of(ALEX),
                Optional.of(reason),
                NOW,
                Optional.empty(),
                Optional.empty()));

        List<SanctionHistoryEntry> stored = history.muteHistory(SAM, 10);
        assertThat(stored)
                .singleElement()
                .satisfies(entry -> assertThat(entry.reason()).hasValueSatisfying(kept -> {
                    assertThat(kept).hasSizeLessThanOrEqualTo(256);
                    assertThat(reason).startsWith(kept);
                }));
    }

    @Test
    void aBatchIsStoredWholeWithKeysAfterTheRowsAlreadyThere() {
        SanctionHistory history = openStore();
        history.append(row(SanctionAction.WARN, SAM, Optional.empty(), NOW));

        history.appendAll(List.of(
                row(SanctionAction.KICK, SAM, Optional.empty(), NOW.plusSeconds(1)),
                row(SanctionAction.KICK, ROBIN, Optional.empty(), NOW.plusSeconds(1))));

        assertThat(history.recentForTarget(SAM, 10))
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.KICK, SanctionAction.WARN);
        assertThat(history.recentForTarget(ROBIN, 10)).hasSize(1);
        assertThat(history.recentByActor(ALEX.uuid(), 10)).hasSize(3);
    }

    @Test
    void theRevocableReadSkipsKicksLiftsAndIpRowsBeforeTheLimit() {
        SanctionHistory history = openStore();
        history.append(row(SanctionAction.BAN, SAM, Optional.empty(), NOW));
        history.append(row(SanctionAction.MUTE, ROBIN, Optional.empty(), NOW.plusSeconds(1)));
        history.append(row(SanctionAction.BAN, SAM, Optional.of("198.51.100.9"), NOW.plusSeconds(2)));
        history.append(row(SanctionAction.UNMUTE, ROBIN, Optional.empty(), NOW.plusSeconds(3)));
        history.appendAll(List.of(
                row(SanctionAction.KICK, SAM, Optional.empty(), NOW.plusSeconds(4)),
                row(SanctionAction.KICK, ROBIN, Optional.empty(), NOW.plusSeconds(4))));

        assertThat(history.revocableByActor(ALEX.uuid(), 2))
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.MUTE, SanctionAction.BAN);
    }

    private static SanctionHistoryEntry row(SanctionAction action, UUID target, Optional<String> ip, Instant at) {
        return new SanctionHistoryEntry(action, target, Issuer.of(ALEX), Optional.empty(), at, Optional.empty(), ip);
    }

    @Test
    void theCountsTakeEveryRowInTheWindowAndCountActionsNotRows() {
        SanctionHistory history = openStore();
        // A STRICT /ban: the player's row and two addresses, one instant.
        history.append(row(SanctionAction.BAN, SAM, Optional.empty(), NOW));
        history.append(row(SanctionAction.BAN, SAM, Optional.of("198.51.100.9"), NOW));
        history.append(row(SanctionAction.BAN, SAM, Optional.of("203.0.113.4"), NOW));
        // A /banip of a bare address and a /banip of a player: each is a ban of its own.
        history.append(row(SanctionAction.BAN, NO_PLAYER, Optional.of("192.0.2.1"), NOW.plusSeconds(1)));
        history.append(row(SanctionAction.BAN, ROBIN, Optional.of("192.0.2.2"), NOW.plusSeconds(2)));
        // A /kickall of three, then two single kicks.
        history.appendAll(List.of(
                row(SanctionAction.KICK, SAM, Optional.empty(), NOW.plusSeconds(3)),
                row(SanctionAction.KICK, ROBIN, Optional.empty(), NOW.plusSeconds(3)),
                row(SanctionAction.KICK, NO_PLAYER, Optional.empty(), NOW.plusSeconds(3))));
        history.append(row(SanctionAction.KICK, SAM, Optional.empty(), NOW.plusSeconds(4)));
        history.append(row(SanctionAction.KICK, ROBIN, Optional.empty(), NOW.plusSeconds(5)));
        history.append(row(SanctionAction.MUTE, SAM, Optional.empty(), NOW.plusSeconds(6)));
        history.append(row(SanctionAction.UNBAN, SAM, Optional.empty(), NOW.plusSeconds(7)));

        assertThat(countsOf(history.countsSince(Instant.EPOCH, Optional.of(ALEX.uuid()))))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        SanctionAction.BAN, 3,
                        SanctionAction.KICK, 3,
                        SanctionAction.MUTE, 1));
    }

    @Test
    void theCountsKeepToTheWindowAndToTheStaffMemberAsked() {
        SanctionHistory history = openStore();
        history.append(row(SanctionAction.WARN, SAM, Optional.empty(), NOW));
        history.append(row(SanctionAction.WARN, SAM, Optional.empty(), NOW.plusSeconds(10)));
        history.append(new SanctionHistoryEntry(
                SanctionAction.WARN,
                SAM,
                Issuer.of(BEA),
                Optional.empty(),
                NOW.plusSeconds(10),
                Optional.empty(),
                Optional.empty()));

        assertThat(history.countsSince(NOW.plusSeconds(5), Optional.of(ALEX.uuid())))
                .singleElement()
                .satisfies(tally -> {
                    assertThat(tally.actorName()).isEqualTo("Alex");
                    assertThat(tally.count()).isEqualTo(1);
                    assertThat(tally.latest()).isEqualTo(NOW.plusSeconds(10));
                });
        assertThat(history.countsSince(Instant.EPOCH, Optional.empty())).hasSize(2);
    }

    @Test
    void aStrictBanByTheConsoleCountsOnceToo() {
        SanctionHistory history = openStore();
        Issuer console = Issuer.console("CONSOLE");
        for (Optional<String> ip : List.of(Optional.<String>empty(), Optional.of("198.51.100.9"))) {
            history.append(new SanctionHistoryEntry(
                    SanctionAction.BAN, SAM, console, Optional.empty(), NOW, Optional.empty(), ip));
        }

        assertThat(history.countsSince(Instant.EPOCH, Optional.empty()))
                .singleElement()
                .satisfies(tally -> {
                    assertThat(tally.actor()).isEmpty();
                    assertThat(tally.count()).isEqualTo(1);
                });
    }

    @Test
    void aKeyAnotherWriterTookFirstIsTakenAgainAndTheRowKept() {
        SanctionHistory first = new JooqSanctionHistory(dsl);
        first.append(row(SanctionAction.WARN, SAM, Optional.empty(), NOW));
        // The second writer read the maximum before the first committed, so its first key is already taken.
        SanctionHistory second = new JooqSanctionHistory(dsl, staleOnce(1L));

        second.appendAll(List.of(
                row(SanctionAction.KICK, SAM, Optional.empty(), NOW.plusSeconds(1)),
                row(SanctionAction.KICK, ROBIN, Optional.empty(), NOW.plusSeconds(1))));
        new JooqSanctionHistory(dsl, staleOnce(1L)).append(row(SanctionAction.MUTE, SAM, Optional.empty(), NOW));

        assertThat(first.recentByActor(ALEX.uuid(), 10)).hasSize(4);
    }

    @Test
    void aKeyThatStaysTakenGivesUpAfterTheLastAttempt() {
        new JooqSanctionHistory(dsl).append(row(SanctionAction.WARN, SAM, Optional.empty(), NOW));
        AtomicInteger reads = new AtomicInteger();
        SanctionHistory alwaysStale = new JooqSanctionHistory(dsl, ignored -> {
            reads.incrementAndGet();
            return 1L;
        });

        assertThatThrownBy(() -> alwaysStale.append(row(SanctionAction.MUTE, SAM, Optional.empty(), NOW)))
                .isInstanceOf(PersistenceException.class);
        assertThat(reads).hasValue(JooqSanctionHistory.WRITE_ATTEMPTS);
    }

    /** A key read that hands out {@code taken} once, as a writer that lost the race would, then reads truly. */
    private static ToLongFunction<DSLContext> staleOnce(long taken) {
        AtomicInteger calls = new AtomicInteger();
        return dsl -> {
            if (calls.getAndIncrement() == 0) {
                return taken;
            }
            Long max = dsl.select(DSL.max(MODERATION_SANCTION_HISTORY.ID))
                    .from(MODERATION_SANCTION_HISTORY)
                    .fetchOne(0, Long.class);
            return (max == null ? 0L : max) + 1L;
        };
    }

    private static Map<SanctionAction, Integer> countsOf(List<SanctionHistory.Tally> tallies) {
        Map<SanctionAction, Integer> counts = new EnumMap<>(SanctionAction.class);
        tallies.forEach(tally -> counts.merge(tally.action(), tally.count(), Integer::sum));
        return counts;
    }

    private SanctionHistory openStore() {
        return new JooqSanctionHistory(dsl);
    }
}

package com.uxplima.uxmessentials.shared.application.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.PendingPlayerNotices;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerNotice;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmessentials.testing.ManualScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import org.junit.jupiter.api.Test;

/**
 * A line for a player online here is shown at once; one for a player who is away waits and is shown once, when they
 * are next online. A player who left before it was shown keeps it.
 */
class DeferredPlayerNoticesTest {

    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final MessageKey PAID = () -> "economy.pay-received";
    private static final MessageLine LINE = new MessageLine(PAID, Map.of("player", "Alex", "amount", "$40.00"));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-01T12:00:00Z"), ZoneOffset.UTC);

    private final Online online = new Online();
    private final Kept kept = new Kept();
    private final Chat chat = new Chat();
    private final List<String> shown = chat.shown;
    private final ManualScheduler lanes = new ManualScheduler();
    private final QuietLog log = new QuietLog();
    private final DeferredPlayerNotices notices = noticesShowing(10);

    private DeferredPlayerNotices noticesShowing(int atOnce) {
        return new DeferredPlayerNotices(
                kept,
                online,
                new Notifier(
                        (viewer, key, args) -> args.containsKey("count")
                                ? key.key() + " " + args.get("count")
                                : key.key() + " " + args.get("player") + " " + args.get("amount"),
                        chat),
                lanes,
                log,
                CLOCK,
                atOnce);
    }

    private static MessageLine paid(String amount) {
        return new MessageLine(PAID, Map.of("player", "Alex", "amount", amount));
    }

    @Test
    void aPlayerBackToManyLinesIsShownTheNewestAndToldHowManyMoreThereWere() {
        DeferredPlayerNotices showingTwo = noticesShowing(2);
        for (int i = 1; i <= 5; i++) {
            showingTwo.tell(SAM, paid("$" + i));
        }
        online.here.add(SAM.uuid());

        assertThat(showingTwo.deliver(SAM)).isEqualTo(5);

        assertThat(shown)
                .containsExactly(
                        "Sam: common.notices-more 3",
                        "Sam: economy.pay-received Alex $4",
                        "Sam: economy.pay-received Alex $5");
        assertThat(kept.rows.getOrDefault(SAM.uuid(), List.of())).isEmpty();
    }

    @Test
    void aStoreThatCannotKeepALineDoesNotFailTheTell() {
        kept.refuse = true;

        notices.tell(SAM, LINE);

        assertThat(log.errors()).hasSize(1);
        assertThat(shown).isEmpty();
    }

    @Test
    void aStoreThatCannotHandBackLinesDoesNotFailTheJoin() {
        kept.refuseTake = true;
        online.here.add(SAM.uuid());

        assertThat(notices.deliver(SAM)).isZero();

        assertThat(log.errors()).hasSize(1);
    }

    @Test
    void aLineThatFailedToShowIsKeptAndTheRestAreStillShown() {
        notices.tell(SAM, paid("$1"));
        notices.tell(SAM, paid("$2"));
        notices.tell(SAM, paid("$3"));
        online.here.add(SAM.uuid());
        chat.failOn = "$2";

        notices.deliver(SAM);

        assertThat(shown).containsExactly("Sam: economy.pay-received Alex $1", "Sam: economy.pay-received Alex $3");
        assertThat(kept.rows.get(SAM.uuid()))
                .singleElement()
                .satisfies(notice -> assertThat(notice.line()).isEqualTo(paid("$2")));
    }

    @Test
    void linesTakenForAPlayerTheChatCannotReachAreKeptAgain() {
        DeferredPlayerNotices showingTwo = noticesShowing(2);
        for (int i = 1; i <= 3; i++) {
            showingTwo.tell(SAM, paid("$" + i));
        }
        online.here.add(SAM.uuid());
        chat.unreachable = true;

        showingTwo.deliver(SAM);
        lanes.runAsync();

        assertThat(shown).isEmpty();
        assertThat(kept.rows.get(SAM.uuid()))
                .extracting(PlayerNotice::line)
                .containsExactlyInAnyOrder(paid("$1"), paid("$2"), paid("$3"));
    }

    @Test
    void aLineThatCannotBeShownToAPlayerHereIsKept() {
        online.here.add(SAM.uuid());
        chat.failOn = "$40.00";

        notices.tell(SAM, LINE);

        assertThat(shown).isEmpty();
        assertThat(kept.rows.get(SAM.uuid()))
                .singleElement()
                .satisfies(notice -> assertThat(notice.line()).isEqualTo(LINE));
    }

    @Test
    void aLineForAPlayerStillLoggingInIsKeptRatherThanLost() {
        online.here.add(SAM.uuid());
        chat.unreachable = true;

        notices.tell(SAM, LINE);
        lanes.runAsync();

        assertThat(shown).isEmpty();
        assertThat(kept.rows.get(SAM.uuid()))
                .singleElement()
                .satisfies(notice -> assertThat(notice.line()).isEqualTo(LINE));
    }

    @Test
    void aPlayerOnlineHereIsToldAtOnce() {
        online.here.add(SAM.uuid());

        notices.tell(SAM, LINE);

        assertThat(shown).containsExactly("Sam: economy.pay-received Alex $40.00");
        assertThat(kept.rows).isEmpty();
    }

    @Test
    void aPlayerWhoIsAwayIsToldOnceWhenTheyAreNextOnline() {
        notices.tell(SAM, LINE);
        assertThat(shown).isEmpty();

        online.here.add(SAM.uuid());
        assertThat(notices.deliver(SAM)).isEqualTo(1);
        assertThat(notices.deliver(SAM)).isZero();

        assertThat(shown).containsExactly("Sam: economy.pay-received Alex $40.00");
    }

    @Test
    void aPlayerWhoLeftBeforeTheLineWasShownKeepsIt() {
        notices.tell(SAM, LINE);

        notices.deliver(SAM);

        assertThat(shown).isEmpty();
        assertThat(kept.rows.get(SAM.uuid())).singleElement().satisfies(notice -> {
            assertThat(notice.line()).isEqualTo(LINE);
            assertThat(notice.at()).isEqualTo(CLOCK.instant());
        });
    }

    /** Who is online on this server. */
    private static final class Online implements PlayerLookup {

        private final Set<UUID> here = new HashSet<>();

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return here.contains(uuid);
        }
    }

    /** The kept lines, as the database holds them; it can be made to refuse, as a database that is down does. */
    private static final class Kept implements PendingPlayerNotices {

        private final Map<UUID, List<PlayerNotice>> rows = new HashMap<>();
        private boolean refuse;
        private boolean refuseTake;

        @Override
        public void keep(PlayerNotice notice) {
            if (refuse) {
                throw new IllegalStateException("database unreachable");
            }
            rows.computeIfAbsent(notice.recipient(), id -> new ArrayList<>()).add(notice);
        }

        @Override
        public List<PlayerNotice> take(UUID recipient) {
            if (refuseTake) {
                throw new IllegalStateException("database unreachable");
            }
            List<PlayerNotice> taken = rows.remove(recipient);
            return taken == null ? List.of() : new ArrayList<>(taken);
        }

        @Override
        public int purgeOlderThan(Instant cutoff) {
            return 0;
        }
    }

    /** The chat: what each player was shown, failing on one line or reaching nobody when the test says so. */
    private static final class Chat implements MessageSink {

        private final List<String> shown = new ArrayList<>();
        private String failOn = "";
        private boolean unreachable;

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            if (!failOn.isEmpty() && renderedText.endsWith(failOn)) {
                throw new IllegalStateException("the chat refused the line");
            }
            shown.add(viewer.name() + ": " + renderedText);
        }

        @Override
        public void deliver(PlayerRef viewer, String renderedText, Runnable unreached) {
            if (unreachable) {
                unreached.run();
                return;
            }
            deliver(viewer, renderedText);
        }
    }
}

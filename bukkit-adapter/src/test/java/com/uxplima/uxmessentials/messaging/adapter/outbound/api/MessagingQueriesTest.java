package com.uxplima.uxmessentials.messaging.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmIgnore;
import com.uxplima.uxmessentials.api.view.UxmIgnoreScope;
import com.uxplima.uxmessentials.api.view.UxmMail;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreEntry;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published messaging query: mail and ignores wait on the database, the two switches answer straight away,
 * and reading somebody's mailbox leaves every item exactly as unread as it was.
 */
class MessagingQueriesTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final Instant NOON = Instant.parse("2026-08-09T12:00:00Z");

    private FakeMailRepository mail;
    private FakeIgnoreStore ignores;
    private FlagToggles toggles;
    private FlagSpies socialSpy;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        mail = new FakeMailRepository();
        ignores = new FakeIgnoreStore();
        toggles = new FlagToggles();
        socialSpy = new FlagSpies();
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @Test
    void everyDurableReadRunsOffTheCallingThread() {
        queries().mailbox(ALICE.uuid()).join();
        queries().unreadMail(ALICE.uuid()).join();
        queries().ignoreList(ALICE.uuid()).join();
        queries().ignores(ALICE.uuid(), BOB.uuid()).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(4);
    }

    @Test
    void theTwoSwitchesAnswerWithoutWaiting() {
        assertThat(queries().acceptsMessages(ALICE.uuid())).isTrue();
        assertThat(queries().isSocialSpying(ALICE.uuid())).isFalse();
        assertThat(scheduler.asyncCalls())
                .as("these are session flags held against the player, so there is nothing to wait for")
                .isZero();
    }

    @Test
    void aMailItemCarriesItsSenderItsTextAndWhetherItWasRead() {
        mail.put(item(1L, MailSender.player(BOB), "meet me at spawn", false));

        UxmMail letter = queries().mailbox(ALICE.uuid()).join().getFirst();

        assertThat(letter.id()).isEqualTo(1L);
        assertThat(letter.recipientId()).isEqualTo(ALICE.uuid());
        assertThat(letter.senderId()).contains(BOB.uuid());
        assertThat(letter.senderName()).isEqualTo("Bob");
        assertThat(letter.body()).isEqualTo("meet me at spawn");
        assertThat(letter.sentAt()).isEqualTo(NOON);
        assertThat(letter.read()).isFalse();
        assertThat(letter.fromPlayer()).isTrue();
    }

    @Test
    void mailFromTheConsoleHasANameAndNoAccount() {
        mail.put(item(2L, MailSender.system("Server"), "scheduled restart", false));

        UxmMail letter = queries().mailbox(ALICE.uuid()).join().getFirst();

        assertThat(letter.senderId()).isEmpty();
        assertThat(letter.senderName()).isEqualTo("Server");
        assertThat(letter.fromPlayer()).isFalse();
    }

    @Test
    void readingAMailboxDoesNotMarkAnythingRead() {
        mail.put(item(1L, MailSender.player(BOB), "hello", false));

        queries().mailbox(ALICE.uuid()).join();

        assertThat(queries().unreadMail(ALICE.uuid()).join())
                .as("marking mail read is the recipient's to do, not a consumer's side effect")
                .isEqualTo(1L);
    }

    @Test
    void anEmptyMailboxIsAnEmptyListRatherThanAnAbsentOne() {
        assertThat(queries().mailbox(ALICE.uuid()).join()).isEmpty();
        assertThat(queries().unreadMail(ALICE.uuid()).join()).isZero();
    }

    @Test
    void anIgnoreEntryCarriesItsScope() {
        ignores.put(new IgnoreEntry(BOB, IgnoreScope.MESSAGES));

        UxmIgnore entry = queries().ignoreList(ALICE.uuid()).join().getFirst();

        assertThat(entry.playerId()).isEqualTo(BOB.uuid());
        assertThat(entry.scope()).isEqualTo(UxmIgnoreScope.MESSAGES);
    }

    @Test
    void ignoringIsOneWay() {
        ignores.put(IgnoreEntry.all(BOB));

        assertThat(queries().ignores(ALICE.uuid(), BOB.uuid()).join()).isTrue();
        assertThat(queries().ignores(BOB.uuid(), ALICE.uuid()).join())
                .as("Alice ignoring Bob says nothing about what Bob hears")
                .isFalse();
    }

    @Test
    void aPlayerWithMessagesTurnedOffIsReportedThatWay() {
        toggles.accepting = false;

        assertThat(queries().acceptsMessages(ALICE.uuid())).isFalse();
    }

    @Test
    void aStaffMemberWithSocialSpyOnIsReportedThatWay() {
        socialSpy.spies.add(ALICE.uuid());

        assertThat(queries().isSocialSpying(ALICE.uuid())).isTrue();
        assertThat(queries().isSocialSpying(BOB.uuid())).isFalse();
    }

    private MessagingQueries queries() {
        return new MessagingQueries(
                mail,
                ignores,
                toggles,
                socialSpy,
                new QueryDoubles.MapLookup().with(ALICE).with(BOB),
                scheduler);
    }

    private static MailItem item(long id, MailSender sender, String body, boolean read) {
        return new MailItem(MailId.of(id), ALICE, sender, MessageBody.of(body), NOON, read);
    }

    /** Holds one recipient's box, with every write left as a trap. */
    private static final class FakeMailRepository implements MailRepository {

        private final List<MailItem> items = new ArrayList<>();

        void put(MailItem item) {
            items.add(item);
        }

        @Override
        public MailBox load(PlayerRef recipient) {
            return MailBox.of(recipient, List.copyOf(items));
        }

        @Override
        public long unreadCount(PlayerRef recipient) {
            return items.stream().filter(item -> !item.read()).count();
        }

        @Override
        public MailItem append(MailItem item) {
            throw new AssertionError("a query must never send mail");
        }

        @Override
        public void markAllRead(PlayerRef recipient) {
            throw new AssertionError("a query must never mark mail read");
        }

        @Override
        public void clear(PlayerRef recipient) {
            throw new AssertionError("a query must never clear a mailbox");
        }

        @Override
        public int deleteSentBefore(Instant cutoff) {
            throw new AssertionError("a query must never expire mail");
        }
    }

    /** One owner's ignore list, which is Alice's; everybody else ignores nobody. */
    private static final class FakeIgnoreStore implements IgnoreStore {

        private final List<IgnoreEntry> entries = new ArrayList<>();

        void put(IgnoreEntry entry) {
            entries.add(entry);
        }

        @Override
        public IgnoreList load(PlayerRef owner) {
            return owner.equals(ALICE) ? IgnoreList.of(owner, List.copyOf(entries)) : IgnoreList.empty(owner);
        }

        @Override
        public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void unignore(PlayerRef owner, PlayerRef ignored) {
            throw new AssertionError("a query must never write");
        }
    }

    /** One flag for everybody, which is enough to prove the switch is read rather than assumed. */
    private static final class FlagToggles implements MessageToggleStore {

        private boolean accepting = true;

        @Override
        public boolean acceptsMessages(PlayerRef who) {
            return accepting;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            throw new AssertionError("a query must never flip a switch");
        }
    }

    /** The set of players spying, with the fan-out path a query never needs left empty. */
    private static final class FlagSpies implements SocialSpyStore {

        private final Set<UUID> spies = new HashSet<>();

        @Override
        public boolean isSpying(PlayerRef who) {
            return spies.contains(who.uuid());
        }

        @Override
        public boolean toggle(PlayerRef who) {
            throw new AssertionError("a query must never flip a switch");
        }

        @Override
        public boolean toggleTarget(PlayerRef spy, PlayerRef target) {
            throw new AssertionError("a query must never flip a switch");
        }

        @Override
        public List<PlayerRef> activeSpies() {
            return List.of();
        }

        @Override
        public Set<PlayerRef> observersOf(PlayerRef sender, PlayerRef target) {
            return Set.of();
        }
    }
}

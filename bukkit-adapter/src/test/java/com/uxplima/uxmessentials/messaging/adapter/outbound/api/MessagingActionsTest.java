package com.uxplima.uxmessentials.messaging.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.messaging.adapter.outbound.InMemoryConversationStore;
import com.uxplima.uxmessentials.messaging.adapter.outbound.InMemorySocialSpyStore;
import com.uxplima.uxmessentials.messaging.application.SendMail;
import com.uxplima.uxmessentials.messaging.application.SendMessage;
import com.uxplima.uxmessentials.messaging.application.port.AfkStatus;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published messaging actions: a message goes through every gate a typed one goes through, mail from a plugin
 * arrives under the plugin's own name, and a recipient who is not here is handled by the operator's policy rather
 * than by this surface.
 */
class MessagingActionsTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private CapturingMail mail;
    private RecordingDelivery delivery;
    private ActionDoubles.InlineScheduler scheduler;
    private boolean offlineToMail;
    private boolean senderMuted;

    @BeforeEach
    void setUp() {
        mail = new CapturingMail();
        delivery = new RecordingDelivery();
        scheduler = new ActionDoubles.InlineScheduler();
        offlineToMail = true;
        senderMuted = false;
    }

    @Test
    void aMessageReachesTheRecipient() {
        UxmOutcome outcome = actions()
                .sendMessage(ALICE.uuid(), BOB.uuid(), "the arena opens in five")
                .join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(delivery.delivered).containsExactly("Bob: the arena opens in five");
    }

    @Test
    void aSenderWhoIsNotOnlineHasNothingToEchoTo() {
        UxmOutcome outcome =
                actions().sendMessage(UUID.randomUUID(), BOB.uuid(), "hello").join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.PLAYER_OFFLINE)).isTrue();
        assertThat(delivery.delivered).isEmpty();
    }

    @Test
    void aRecipientWhoIsNotHereGetsMailWhenTheServerTurnsMessagesIntoMail() {
        UxmOutcome outcome = actions()
                .sendMessage(ALICE.uuid(), UUID.randomUUID(), "back later")
                .join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(mail.appended).hasSize(1);
    }

    @Test
    void aRecipientWhoIsNotHereIsRefusedWhenItDoesNot() {
        offlineToMail = false;

        UxmOutcome outcome = actions()
                .sendMessage(ALICE.uuid(), UUID.randomUUID(), "back later")
                .join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.PLAYER_OFFLINE)).isTrue();
        assertThat(mail.appended).isEmpty();
    }

    @Test
    void aMutedSenderIsRefusedTheSameWayTheCommandRefusesThem() {
        senderMuted = true;

        UxmOutcome outcome =
                actions().sendMail(ALICE.uuid(), BOB.uuid(), "a note").join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.REFUSED)).isTrue();
        assertThat(mail.appended).isEmpty();
    }

    @Test
    void mailFromOnePlayerToAnotherIsStoredUnderTheSendersName() {
        actions().sendMail(ALICE.uuid(), BOB.uuid(), "see you at spawn").join();

        assertThat(mail.appended).hasSize(1);
        assertThat(mail.appended.getFirst().sender().name()).isEqualTo("Alice");
        assertThat(mail.appended.getFirst().sender().uuid()).contains(ALICE.uuid());
    }

    @Test
    void mailFromThePluginArrivesUnderThePluginsOwnName() {
        UxmOutcome outcome =
                actions().sendMail(BOB.uuid(), "your prize is waiting").join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(mail.appended.getFirst().sender().name()).isEqualTo("TestPlugin");
        assertThat(mail.appended.getFirst().sender().uuid())
                .as("no account sent it, so no account is recorded as having done so")
                .isEmpty();
    }

    @Test
    void nothingMutesAPlugin() {
        senderMuted = true;

        UxmOutcome outcome = actions()
                .sendMail(BOB.uuid(), "the server is going down in five")
                .join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(mail.appended).hasSize(1);
    }

    @Test
    void aBodyNoMailColumnCouldHoldIsABugRatherThanAnAnswer() {
        String tooLong = "x".repeat(MessageBody.MAX_LENGTH + 1);

        assertThatThrownBy(() -> actions().sendMail(BOB.uuid(), tooLong)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> actions().sendMail(BOB.uuid(), "   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyWriteRunsOffTheCallingThread() {
        actions().sendMessage(ALICE.uuid(), BOB.uuid(), "one").join();
        actions().sendMail(ALICE.uuid(), BOB.uuid(), "two").join();
        actions().sendMail(BOB.uuid(), "three").join();

        assertThat(scheduler.asyncCalls()).isEqualTo(3);
        assertThat(scheduler.entityCalls()).isZero();
    }

    private MessagingActions actions() {
        ActionDoubles.RecordingEvents events = new ActionDoubles.RecordingEvents();
        MutePolicy mute = who -> senderMuted;
        SendMessage message = new SendMessage(
                delivery,
                new NoIgnores(),
                new InMemoryConversationStore(),
                new AcceptingToggles(),
                new InMemorySocialSpyStore(),
                mute,
                AfkStatus.NEVER,
                mail,
                offlineToMail,
                ActionDoubles.silentNotifier(),
                events,
                CLOCK);
        SendMail post =
                new SendMail(mail, new NoIgnores(), delivery, mute, ActionDoubles.silentNotifier(), events, CLOCK);
        return new MessagingActions(
                new MessagingApiWrites(message, post),
                new QueryDoubles.MapLookup().with(ALICE).with(BOB),
                scheduler,
                "TestPlugin");
    }

    /** Remembers what reached a player, since a delivered message leaves nothing else behind. */
    private static final class RecordingDelivery implements MessageDelivery {

        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliverMessage(PlayerRef sender, PlayerRef recipient, MessageBody body) {
            delivered.add(recipient.name() + ": " + body.value());
        }

        @Override
        public void echoSent(PlayerRef sender, PlayerRef recipient, MessageBody body) {}

        @Override
        public void notifyNewMail(PlayerRef recipient, MailItem item) {}

        @Override
        public void deliverMailLine(PlayerRef reader, MailItem item) {}

        @Override
        public void deliverHelpOp(PlayerRef requester, PlayerRef viewer, MessageBody body) {}

        @Override
        public void deliverSpy(PlayerRef observer, PlayerRef sender, PlayerRef recipient, MessageBody body) {}
    }

    /** Keeps every stored item, which is the whole of what mail leaves behind. */
    private static final class CapturingMail implements MailRepository {

        private final List<MailItem> appended = new ArrayList<>();

        @Override
        public MailBox load(PlayerRef recipient) {
            return MailBox.empty(recipient);
        }

        @Override
        public long unreadCount(PlayerRef recipient) {
            return 0;
        }

        @Override
        public MailItem append(MailItem item) {
            MailItem stored = item.withId(MailId.of(appended.size() + 1L));
            appended.add(stored);
            return stored;
        }

        @Override
        public void markAllRead(PlayerRef recipient) {}

        @Override
        public void clear(PlayerRef recipient) {}

        @Override
        public int deleteSentBefore(Instant cutoff) {
            return 0;
        }
    }

    private static final class NoIgnores implements IgnoreStore {

        @Override
        public IgnoreList load(PlayerRef owner) {
            return IgnoreList.empty(owner);
        }

        @Override
        public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {}

        @Override
        public void unignore(PlayerRef owner, PlayerRef ignored) {}
    }

    private static final class AcceptingToggles implements MessageToggleStore {

        @Override
        public boolean acceptsMessages(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }
    }
}

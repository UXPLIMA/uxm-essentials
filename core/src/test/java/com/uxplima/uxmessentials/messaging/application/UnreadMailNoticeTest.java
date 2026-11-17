package com.uxplima.uxmessentials.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.QuietLog;
import org.junit.jupiter.api.Test;

/**
 * A player who joins is told how much mail waits for them unread, and nothing when none does or when they only moved
 * here from another server of the network.
 */
class UnreadMailNoticeTest {

    private static final PlayerRef ALEX = new PlayerRef(UUID.randomUUID(), "Alex");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    private final Map<UUID, Long> unread = new HashMap<>();
    private final List<String> lines = new ArrayList<>();
    private final QuietLog log = new QuietLog();
    private final Map<UUID, String> cameFrom = new HashMap<>();
    private final UnreadMailNotice notice = new UnreadMailNotice(new Box(), new Lines(), log, new Presence());

    @Test
    void aPlayerWithUnreadMailIsToldHowMuch() {
        unread.put(ALEX.uuid(), 3L);

        notice.joined(ALEX);

        assertThat(lines).containsExactly("Alex: 3 unread");
    }

    @Test
    void aPlayerMovingHereFromAnotherServerIsNotToldAgainButOneJoiningIs() {
        cameFrom.put(ALEX.uuid(), "lobby-1");

        assertThat(notice.moving(ALEX)).isTrue();
        assertThat(notice.moving(BOB)).isFalse();
    }

    @Test
    void aPlayerWithNoUnreadMailIsToldNothing() {
        notice.joined(BOB);

        assertThat(lines).isEmpty();
    }

    @Test
    void aCountThatCannotBeReadIsLoggedAndTellsNothing() {
        unread.put(ALEX.uuid(), -1L);

        notice.joined(ALEX);

        assertThat(lines).isEmpty();
        assertThat(log.warnings()).hasSize(1);
    }

    private final class Box implements MailRepository {

        @Override
        public MailBox load(PlayerRef recipient) {
            return MailBox.empty(recipient);
        }

        @Override
        public long unreadCount(PlayerRef recipient) {
            long count = unread.getOrDefault(recipient.uuid(), 0L);
            if (count < 0) {
                throw new IllegalStateException("database down");
            }
            return count;
        }

        @Override
        public MailItem append(MailItem item) {
            return item;
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

    /** The network as this server heard it: only where a joining player came from matters here. */
    private final class Presence implements NetworkPresence {

        @Override
        public String localServer() {
            return "survival-1";
        }

        @Override
        public Optional<OnlinePlayer> find(UUID player) {
            return Optional.empty();
        }

        @Override
        public Optional<OnlinePlayer> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public List<OnlinePlayer> onServer(String server) {
            return List.of();
        }

        @Override
        public List<OnlinePlayer> online() {
            return List.of();
        }

        @Override
        public Optional<String> cameFrom(UUID player, Duration within) {
            return Optional.ofNullable(cameFrom.get(player));
        }
    }

    private final class Lines implements MessageDelivery {

        @Override
        public void deliverMessage(PlayerRef sender, PlayerRef recipient, MessageBody body) {}

        @Override
        public void echoSent(PlayerRef sender, PlayerRef recipient, MessageBody body) {}

        @Override
        public void notifyNewMail(PlayerRef recipient, String senderName) {}

        @Override
        public void notifyUnreadMail(PlayerRef recipient, long count) {
            lines.add(recipient.name() + ": " + count + " unread");
        }

        @Override
        public void deliverMailLine(PlayerRef reader, MailItem item) {}

        @Override
        public void deliverHelpOp(PlayerRef requester, PlayerRef viewer, MessageBody body) {}

        @Override
        public void deliverRemoteHelpOp(PlayerRef requester, String server, PlayerRef viewer, MessageBody body) {}

        @Override
        public void deliverSpy(PlayerRef observer, PlayerRef sender, PlayerRef recipient, MessageBody body) {}
    }
}

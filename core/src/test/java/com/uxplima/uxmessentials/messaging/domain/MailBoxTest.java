package com.uxplima.uxmessentials.messaging.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The {@link MailBox} aggregate invariants: newest-first ordering, unread counting, mark-all-read, and the
 * mail-item read/expiry value semantics. The aggregate is pure and immutable between operations, so each rule
 * is asserted here in isolation from the repository.
 */
class MailBoxTest {

    private static final PlayerRef RECIPIENT = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final MailSender SENDER = MailSender.system("Server");
    private static final Instant NOW = Instant.parse("2026-05-30T12:00:00Z");

    @Test
    void anEmptyBoxReportsEmptyAndZeroUnread() {
        MailBox box = MailBox.empty(RECIPIENT);

        assertThat(box.isEmpty()).isTrue();
        assertThat(box.unreadCount()).isZero();
        assertThat(box.hasUnread()).isFalse();
    }

    @Test
    void itemsAreOrderedNewestFirstRegardlessOfInputOrder() {
        MailItem older = item("first", NOW);
        MailItem newer = item("second", NOW.plusSeconds(60));
        MailBox box = MailBox.of(RECIPIENT, List.of(older, newer));

        assertThat(box.items().stream().map(i -> i.body().value())).containsExactly("second", "first");
    }

    @Test
    void unreadCountReflectsUnreadItemsOnly() {
        MailBox box = MailBox.of(RECIPIENT, List.of(item("a", NOW), item("b", NOW.plusSeconds(1))));

        assertThat(box.size()).isEqualTo(2);
        assertThat(box.unreadCount()).isEqualTo(2);
        assertThat(box.hasUnread()).isTrue();
    }

    @Test
    void markAllReadClearsTheUnreadCount() {
        MailBox box = MailBox.of(RECIPIENT, List.of(item("a", NOW), item("b", NOW.plusSeconds(1))));

        MailBox afterRead = box.markAllRead();

        assertThat(afterRead.unreadCount()).isZero();
        assertThat(afterRead.hasUnread()).isFalse();
        assertThat(afterRead.size()).isEqualTo(2); // mark-read does not remove items
    }

    @Test
    void withAppendsAndRestoresNewestFirstOrder() {
        MailBox box = MailBox.of(RECIPIENT, List.of(item("old", NOW)));

        MailBox grown = box.with(item("new", NOW.plusSeconds(120)));

        assertThat(grown.items().stream().map(i -> i.body().value())).containsExactly("new", "old");
    }

    @Test
    void aMarkReadItemIsValueEqualAcrossTheReadFlag() {
        MailItem unread = item("hi", NOW);

        assertThat(unread.read()).isFalse();
        assertThat(unread.markRead().read()).isTrue();
        // marking an already-read item is idempotent (returns the same value)
        assertThat(unread.markRead().markRead()).isEqualTo(unread.markRead());
    }

    @Test
    void expiryIsMeasuredFromSendTimeAgainstTheRetentionWindow() {
        MailItem item = item("hi", NOW);

        assertThat(item.isExpired(NOW.plus(Duration.ofDays(31)), Duration.ofDays(30)))
                .isTrue();
        assertThat(item.isExpired(NOW.plus(Duration.ofDays(29)), Duration.ofDays(30)))
                .isFalse();
        // a non-positive retention never expires
        assertThat(item.isExpired(NOW.plus(Duration.ofDays(365)), Duration.ZERO))
                .isFalse();
    }

    private static MailItem item(String body, Instant sentAt) {
        return MailItem.compose(RECIPIENT, SENDER, MessageBody.of(body), sentAt);
    }
}

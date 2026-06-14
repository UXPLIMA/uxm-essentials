package com.uxplima.uxmessentials.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The {@code /mail sendall} broadcast use case against an in-memory mail repository: one durable mail row per
 * recipient from the broadcaster, and the stored count returned. An empty recipient set stores nothing.
 */
class SendMailToAllTest {

    private static final Instant T0 = Instant.parse("2026-06-14T12:00:00Z");
    private final PlayerRef staff = new PlayerRef(UUID.randomUUID(), "Staff");
    private final PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
    private final PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
    private final MessageBody body = MessageBody.of("server restart in 5m");

    private final CapturingMail mail = new CapturingMail();
    private final SendMailToAll sendAll = new SendMailToAll(mail, Clock.fixed(T0, ZoneOffset.UTC));

    @Test
    void storesOneMailPerRecipientAndReturnsTheCount() {
        int sent = sendAll.sendToAll(staff, body, List.of(alice, bob));

        assertThat(sent).isEqualTo(2);
        assertThat(mail.appended).hasSize(2);
        assertThat(mail.appended).allSatisfy(item -> {
            assertThat(item.body()).isEqualTo(body);
            assertThat(item.sender().name()).isEqualTo(staff.name());
            assertThat(item.sentAt()).isEqualTo(T0);
        });
        assertThat(mail.appended.stream().map(MailItem::recipient)).containsExactlyInAnyOrder(alice, bob);
    }

    @Test
    void anEmptyRecipientSetStoresNothingAndReturnsZero() {
        int sent = sendAll.sendToAll(staff, body, List.of());

        assertThat(sent).isZero();
        assertThat(mail.appended).isEmpty();
    }

    private static final class CapturingMail implements MailRepository {
        final List<MailItem> appended = new ArrayList<>();

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
}

package com.uxplima.uxmessentials.migration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** Mail kept in a list, standing in for the mail table: ids are assigned on append, as the store does. */
final class InMemoryMail implements MailRepository {

    private final List<MailItem> items = new ArrayList<>();

    List<MailItem> all() {
        return List.copyOf(items);
    }

    @Override
    public MailBox load(PlayerRef recipient) {
        return MailBox.of(
                recipient,
                items.stream()
                        .filter(item -> item.recipient().uuid().equals(recipient.uuid()))
                        .toList());
    }

    @Override
    public long unreadCount(PlayerRef recipient) {
        return load(recipient).unreadCount();
    }

    @Override
    public MailItem append(MailItem item) {
        MailItem stored = item.withId(MailId.of(items.size() + 1L));
        items.add(stored);
        return stored;
    }

    @Override
    public void markAllRead(PlayerRef recipient) {
        items.replaceAll(item -> item.recipient().uuid().equals(recipient.uuid()) ? item.markRead() : item);
    }

    @Override
    public void clear(PlayerRef recipient) {
        items.removeIf(item -> item.recipient().uuid().equals(recipient.uuid()));
    }

    @Override
    public int deleteSentBefore(Instant cutoff) {
        int before = items.size();
        items.removeIf(item -> item.sentAt().isBefore(cutoff));
        return before - items.size();
    }
}

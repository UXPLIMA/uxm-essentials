package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Tells a player who joins how much of their mail they have not read: mail left while they were offline, on any
 * backend of the network, which no live notice could reach. A player with no unread mail is told nothing.
 */
public final class UnreadMailNotice {

    private final MailRepository mail;
    private final MessageDelivery delivery;
    private final Logger log;

    public UnreadMailNotice(MailRepository mail, MessageDelivery delivery, Logger log) {
        this.mail = Objects.requireNonNull(mail, "mail");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Count {@code player}'s unread mail and tell them. Reads storage: call it off the tick. */
    public void joined(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        long unread;
        try {
            unread = mail.unreadCount(player);
        } catch (RuntimeException unreachable) {
            log.warn(
                    "event=mail_unread_count_failed player={} reason={}",
                    player.name(),
                    String.valueOf(unreachable.getMessage()));
            return;
        }
        if (unread > 0) {
            delivery.notifyUnreadMail(player, unread);
        }
    }
}

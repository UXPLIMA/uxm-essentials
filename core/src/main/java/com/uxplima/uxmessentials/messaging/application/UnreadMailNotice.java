package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Tells a player who joins how much of their mail they have not read: mail left while they were offline, on any
 * backend of the network, which no live notice could reach. A player with no unread mail is told nothing, and neither
 * is one moving here from another server of the network: they were online, so a live notice reached them.
 */
public final class UnreadMailNotice {

    private final MailRepository mail;
    private final MessageDelivery delivery;
    private final Logger log;
    private final NetworkPresence presence;

    public UnreadMailNotice(MailRepository mail, MessageDelivery delivery, Logger log, NetworkPresence presence) {
        this.mail = Objects.requireNonNull(mail, "mail");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.log = Objects.requireNonNull(log, "log");
        this.presence = Objects.requireNonNull(presence, "presence");
    }

    /**
     * Whether {@code player}, joining now, is moving here from another server of the network. Asked at the join, since
     * the other server forgets them soon after: memory only, fit for the tick.
     */
    public boolean moving(PlayerRef player) {
        return presence.cameFrom(Objects.requireNonNull(player, "player").uuid(), NetworkPresence.SWITCH_WINDOW)
                .isPresent();
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

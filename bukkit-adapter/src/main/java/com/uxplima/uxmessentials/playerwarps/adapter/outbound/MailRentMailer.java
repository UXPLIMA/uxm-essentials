package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.port.RentMailer;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The rent {@link RentMailer} over the messaging mail store: it resolves the reminder text in the owner's locale
 * and leaves it as a piece of durable, system-sent mail, so an offline owner reads their rent heads-up on next
 * join. This is the one bridge from player-warps into the messaging context, kept in the adapter layer exactly so
 * the {@code :core} rent use cases stay free of any messaging import.
 *
 * <p>Delivery is best-effort: a mail that cannot be resolved or stored is logged and swallowed rather than thrown
 * back into the sweep, so a messaging fault can never suspend the rent loop. The sender name is itself resolved
 * from a message key, so there is no inline user-facing literal anywhere here.
 */
@NullMarked
public final class MailRentMailer implements RentMailer {

    private final MailRepository mail;
    private final Messages messages;
    private final Clock clock;
    private final Logger log;

    public MailRentMailer(MailRepository mail, Messages messages, Clock clock, Logger log) {
        this.mail = Objects.requireNonNull(mail, "mail");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void mail(PlayerRef owner, MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        try {
            String rendered = messages.resolve(owner, key, placeholders).strip();
            if (rendered.isEmpty()) {
                return;
            }
            String senderName = messages.resolve(owner, PlayerwarpsMessageKey.PWARP_RENT_MAIL_SENDER, Map.of());
            MailItem item = MailItem.compose(
                    owner, MailSender.system(senderName), MessageBody.of(clamp(rendered)), clock.instant());
            mail.append(item);
        } catch (RuntimeException failure) {
            log.warn("event=playerwarp_rent_reminder_failed owner={} reason={}", owner.uuid(), failure.toString());
        }
    }

    /** Keep the body within the durable mail column; a resolved reminder is short, so this only guards an edge. */
    private static String clamp(String body) {
        return body.length() <= MessageBody.MAX_LENGTH ? body : body.substring(0, MessageBody.MAX_LENGTH);
    }
}

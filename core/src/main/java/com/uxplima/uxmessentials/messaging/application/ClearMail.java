package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /mail clear} (and the {@code /mailclear} alias): empty the owner's persistent mailbox. An already
 * empty box reports {@link MessagingError#MAILBOX_EMPTY} so the owner gets honest feedback rather than a
 * "cleared" confirmation for a no-op; otherwise every item is deleted and the owner is told how many were
 * removed.
 */
public final class ClearMail {

    private final MailRepository mail;
    private final Notifier notifier;

    public ClearMail(MailRepository mail, Notifier notifier) {
        this.mail = Objects.requireNonNull(mail, "mail");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Clear {@code owner}'s mailbox. */
    public Result<Unit, MessagingError> clear(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        int held = mail.load(owner).size();
        if (held == 0) {
            notifier.send(owner, MessagingError.MAILBOX_EMPTY.messageKey());
            return Result.err(MessagingError.MAILBOX_EMPTY);
        }
        mail.clear(owner);
        notifier.send(owner, MessagingMessageKey.MAIL_CLEARED, java.util.Map.of("count", Integer.toString(held)));
        return Result.ok();
    }
}

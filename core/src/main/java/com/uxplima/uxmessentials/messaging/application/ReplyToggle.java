package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.messaging.domain.MessagingMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;

/**
 * {@code /rtoggle}: flip whether the player takes part in reply routing. With reply routing off, a
 * {@code /reply} aimed at them is declined as if there were no fresh conversation, but a named {@code /msg}
 * still reaches them and mail still delivers: the toggle gates only the back-channel reply convenience. The
 * new state is reported to the player. The flag itself lives in the {@link ReplyRoutingStore}, shared by every
 * backend; this use case flips it and renders the outcome, and refuses with a try-again line while the player's
 * settings are still being read.
 */
public final class ReplyToggle {

    private final ReplyRoutingStore replies;
    private final Notifier notifier;

    public ReplyToggle(ReplyRoutingStore replies, Notifier notifier) {
        this.replies = Objects.requireNonNull(replies, "replies");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Flip {@code who}'s reply-routing flag and tell them the new state. */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (!replies.ready(who)) {
            notifier.send(who, SharedMessageKey.COMMAND_ERROR);
            return replies.acceptsReplies(who);
        }
        boolean acceptsNow = replies.toggle(who);
        notifier.send(who, acceptsNow ? MessagingMessageKey.REPLY_TOGGLE_ON : MessagingMessageKey.REPLY_TOGGLE_OFF);
        return acceptsNow;
    }
}

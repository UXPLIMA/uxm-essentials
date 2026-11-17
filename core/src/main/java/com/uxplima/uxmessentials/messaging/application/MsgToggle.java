package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.domain.MessagingMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;

/**
 * {@code /msgtoggle}: flip whether the player accepts incoming private messages. With DMs off, a {@code /msg}
 * or {@code /reply} to them is refused with a visible reason, but mail still delivers, so the toggle gates
 * only the real-time channel. The new state is reported to the player. The flag itself lives in the
 * {@link MessageToggleStore}, shared by every backend; this use case flips it and renders the outcome, and
 * refuses with a try-again line while the player's settings are still being read.
 */
public final class MsgToggle {

    private final MessageToggleStore toggles;
    private final Notifier notifier;

    public MsgToggle(MessageToggleStore toggles, Notifier notifier) {
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Flip {@code who}'s message-accept flag and tell them the new state. */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (!toggles.ready(who)) {
            notifier.send(who, SharedMessageKey.COMMAND_ERROR);
            return toggles.acceptsMessages(who);
        }
        boolean acceptsNow = toggles.toggle(who);
        notifier.send(who, acceptsNow ? MessagingMessageKey.MSG_TOGGLE_ON : MessagingMessageKey.MSG_TOGGLE_OFF);
        return acceptsNow;
    }
}

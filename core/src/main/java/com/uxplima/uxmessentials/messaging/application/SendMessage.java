package com.uxplima.uxmessentials.messaging.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.ConversationStore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreChannel;
import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.messaging.domain.event.PrivateMessageSent;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /msg <player> <text>}: send a private message, applying every delivery gate in order. The target is
 * already resolved by the command adapter, whose lookup is vanish-aware (a vanished target the sender cannot
 * see is offered as unknown, so its presence is never leaked — the same {@code canSee} seam the teleport
 * context applies to {@code /tpa}). This use case then gates on mute (the moderation soft-couple), self,
 * toggle, and ignore: a target who toggled DMs off rejects with a visible reason; a target who ignores the
 * sender silently declines — the sender's echo still says delivered, so an ignore is not observable,
 * matching the ignore-aware contract.
 *
 * <p>On delivery it echoes to the sender, delivers to the recipient, fans out to active socialspy staff,
 * records the reply target on <em>both</em> sides (either may {@code /reply}), and publishes
 * {@code PrivateMessageSent}. The reply path ({@link Reply}) shares this engine through {@link #deliver}.
 */
public final class SendMessage {

    private final MessageDelivery delivery;
    private final IgnoreStore ignores;
    private final ConversationStore conversations;
    private final MessageToggleStore toggles;
    private final SocialSpyStore socialSpy;
    private final MutePolicy mute;
    private final MessagingNotifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SendMessage(
            MessageDelivery delivery,
            IgnoreStore ignores,
            ConversationStore conversations,
            MessageToggleStore toggles,
            SocialSpyStore socialSpy,
            MutePolicy mute,
            MessagingNotifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.socialSpy = Objects.requireNonNull(socialSpy, "socialSpy");
        this.mute = Objects.requireNonNull(mute, "mute");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Send {@code body} from {@code sender} to {@code target}, applying every gate. */
    public Result<Unit, MessagingError> send(PlayerRef sender, PlayerRef target, MessageBody body) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(body, "body");
        return deliver(sender, target, body);
    }

    /**
     * The shared delivery engine for {@code /msg} and {@code /reply}: gate, then deliver. The target is
     * assumed resolved (online and not hidden) by the caller's lookup; the remaining gates are mute, self,
     * toggle, and ignore.
     */
    Result<Unit, MessagingError> deliver(PlayerRef sender, PlayerRef target, MessageBody body) {
        Result<Unit, MessagingError> gate = gate(sender, target);
        if (gate.isErr()) {
            return reject(sender, gate.errorOrThrow());
        }
        if (ignores.load(target).blocks(sender, IgnoreChannel.MESSAGE)) {
            return silentlyDrop(sender, target, body);
        }
        dispatch(sender, target, body);
        return Result.ok();
    }

    private Result<Unit, MessagingError> gate(PlayerRef sender, PlayerRef target) {
        if (mute.isMuted(sender)) {
            return Result.err(MessagingError.SENDER_MUTED);
        }
        if (sender.equals(target)) {
            return Result.err(MessagingError.SELF);
        }
        if (!toggles.acceptsMessages(target)) {
            return Result.err(MessagingError.TARGET_TOGGLED_OFF);
        }
        return Result.ok();
    }

    private void dispatch(PlayerRef sender, PlayerRef target, MessageBody body) {
        delivery.echoSent(sender, target, body);
        delivery.deliverMessage(sender, target, body);
        fanOutSpies(sender, target, body);
        rememberBothSides(sender, target);
        events.publish(new PrivateMessageSent(sender, target, body, clock.instant()));
    }

    private void fanOutSpies(PlayerRef sender, PlayerRef target, MessageBody body) {
        for (PlayerRef observer : socialSpy.activeSpies()) {
            if (!observer.equals(sender) && !observer.equals(target)) {
                delivery.deliverSpy(observer, sender, target, body);
            }
        }
    }

    private void rememberBothSides(PlayerRef sender, PlayerRef target) {
        java.time.Instant now = clock.instant();
        conversations.remember(sender, LastConversation.with(target, now));
        conversations.remember(target, LastConversation.with(sender, now));
    }

    private Result<Unit, MessagingError> silentlyDrop(PlayerRef sender, PlayerRef target, MessageBody body) {
        // The recipient ignores the sender: do not deliver, but echo as if delivered so the ignore is not
        // observable. Spies still see the attempt; the reply target is still captured for the sender.
        delivery.echoSent(sender, target, body);
        fanOutSpies(sender, target, body);
        conversations.remember(sender, LastConversation.with(target, clock.instant()));
        return Result.ok();
    }

    private Result<Unit, MessagingError> reject(PlayerRef sender, MessagingError error) {
        notifier.send(sender, error.messageKey());
        return Result.err(error);
    }
}

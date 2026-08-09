package com.uxplima.uxmessentials.messaging.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.messaging.UxmHelpOpEvent;
import com.uxplima.uxmessentials.api.bukkit.event.messaging.UxmMailDeliverEvent;
import com.uxplima.uxmessentials.api.bukkit.event.messaging.UxmPrivateMessageEvent;
import com.uxplima.uxmessentials.messaging.domain.event.HelpOpRaised;
import com.uxplima.uxmessentials.messaging.domain.event.MailDelivered;
import com.uxplima.uxmessentials.messaging.domain.event.PrivateMessageSent;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each messaging fact becomes.
 *
 * <p>A private message follows the sender, since that is who is at the keyboard; mail follows the recipient, since
 * the whole point of mail is that the sender may be long gone.
 */
@NullMarked
public final class MessagingEventBridges {

    private MessagingEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                PrivateMessageSent.class,
                UxmPrivateMessageEvent.getHandlerList(),
                fact -> new UxmPrivateMessageEvent(
                        fact.sender().uuid(),
                        fact.sender().name(),
                        fact.recipient().uuid(),
                        fact.recipient().name(),
                        fact.body().value(),
                        fact.sentAt()),
                fact -> Region.entity(fact.sender()));
        registry.register(
                MailDelivered.class,
                UxmMailDeliverEvent.getHandlerList(),
                fact -> new UxmMailDeliverEvent(
                        fact.recipient().uuid(),
                        fact.recipient().name(),
                        fact.sender().uuid(),
                        fact.sender().name(),
                        fact.body().value(),
                        fact.sentAt()),
                fact -> Region.entity(fact.recipient()));
        registry.register(
                HelpOpRaised.class,
                UxmHelpOpEvent.getHandlerList(),
                fact -> new UxmHelpOpEvent(
                        fact.requester().uuid(),
                        fact.requester().name(),
                        fact.body().value(),
                        fact.raisedAt()),
                fact -> Region.entity(fact.requester()));
    }
}

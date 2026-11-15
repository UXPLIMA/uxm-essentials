package com.uxplima.uxmessentials.messaging.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MessagingNetwork;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.messaging.domain.event.HelpOpRaised;
import com.uxplima.uxmessentials.shared.application.RecentIds;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.permission.SocialPermissions;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /helpop <text>}: open a player→staff support request. The send is mute-gated (a muted player cannot
 * raise a help-op) and fans the request out to every online staff member holding the receive node. The
 * requester always gets a sent confirmation even when no staff are online, so a help-op is never lost
 * silently: operators can read the {@code HelpOpRaised} event in their audit trail. The request itself is
 * transient; only the fact that it was raised is a domain event.
 *
 * <p>On a network the request also reaches the staff of every other backend, with the requester's server in the line
 * so staff know where to go. The event is published on the requester's backend only. With no staff here, the
 * requester is told the request was sent as long as another backend has players on it, since its staff may answer.
 * Presence cannot tell staff from other players; the line names nobody, so it reveals at most that another backend
 * is not empty.
 */
public final class HelpOp {

    private static final String RECEIVE_NODE = SocialPermissions.HELPOP_RECEIVE;

    private final StaffAudience staff;
    private final MessageDelivery delivery;
    private final MutePolicy mute;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final MessagingNetwork network;
    private final NetworkPresence presence;
    private final Scheduler scheduler;
    private final RecentIds received = new RecentIds();

    public HelpOp(
            StaffAudience staff,
            MessageDelivery delivery,
            MutePolicy mute,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock,
            MessagingNetwork network,
            NetworkPresence presence,
            Scheduler scheduler) {
        this.staff = Objects.requireNonNull(staff, "staff");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.mute = Objects.requireNonNull(mute, "mute");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.network = Objects.requireNonNull(network, "network");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Raise a help-op from {@code requester} with {@code body}. */
    public Result<Unit, MessagingError> raise(PlayerRef requester, MessageBody body) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(body, "body");
        if (mute.isMuted(requester)) {
            notifier.send(requester, MessagingError.SENDER_MUTED.messageKey());
            return Result.err(MessagingError.SENDER_MUTED);
        }
        fanOut(requester, body);
        events.publish(new HelpOpRaised(requester, body, clock.instant()));
        return Result.ok();
    }

    private void fanOut(PlayerRef requester, MessageBody body) {
        List<PlayerRef> recipients = staff.onlineWith(RECEIVE_NODE);
        for (PlayerRef viewer : recipients) {
            delivery.deliverHelpOp(requester, viewer, body);
        }
        network.raise(new RemoteHelpOp(UUID.randomUUID(), requester, body));
        boolean heard = !recipients.isEmpty() || anyoneElsewhere();
        notifier.send(requester, heard ? MessagingMessageKey.HELPOP_SENT : MessagingMessageKey.HELPOP_NO_STAFF);
    }

    /**
     * A request raised on {@code server}: show it to the staff here. The staff roster is read on the global thread,
     * the one thread where it is consistent on Folia.
     */
    public void receive(RemoteHelpOp request, String server) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(server, "server");
        if (!received.firstTime(request.id())) {
            return;
        }
        scheduler.onGlobal(() -> {
            for (PlayerRef viewer : staff.onlineWith(RECEIVE_NODE)) {
                delivery.deliverRemoteHelpOp(request.requester(), server, viewer, request.body());
            }
        });
    }

    private boolean anyoneElsewhere() {
        String here = presence.localServer();
        return presence.online().stream().anyMatch(player -> !player.server().equals(here));
    }
}

package com.uxplima.uxmessentials.staff.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.RecentIds;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.application.port.StaffChatNetwork;
import com.uxplima.uxmessentials.staff.domain.event.StaffChatSent;

/**
 * {@code /staffchat} (and {@code /sc}) {@code <message>}: send a line on the staff-only channel. The message
 * is fanned out to every online staff member through the soft-coupled {@link StaffChannel}, the same
 * staff-audience fan-out as {@code HelpOp.raise}, and the fact that it was sent is published as a domain
 * event for the audit trail. Staff chat is plain communication: it carries no sanction.
 *
 * <p>When the messaging module is disabled the channel binds {@link StaffChannel#NONE}, so the audience is
 * empty and the send degrades to silence rather than failing; the {@link StaffChatSent} event is still
 * published so the audit trail records the attempt.
 *
 * <p>On a network the line also reaches the staff of every other backend, with the sender's server in the line. The
 * event is published on the sender's backend only.
 */
public final class SendStaffChat {

    private final StaffChannel channel;
    private final DomainEventPublisher events;
    private final StaffChatNetwork network;
    private final Scheduler scheduler;
    private final RecentIds received = new RecentIds();

    public SendStaffChat(
            StaffChannel channel, DomainEventPublisher events, StaffChatNetwork network, Scheduler scheduler) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.events = Objects.requireNonNull(events, "events");
        this.network = Objects.requireNonNull(network, "network");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Fan {@code message} from {@code from} out to the online staff audience. */
    public void send(PlayerRef from, String message) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(message, "message");
        List<PlayerRef> recipients = channel.onlineStaff();
        channel.send(from, recipients, message);
        network.publish(new RemoteStaffChat(UUID.randomUUID(), from, message));
        events.publish(new StaffChatSent(from, message));
    }

    /**
     * A line sent on {@code server}: show it to the staff here. The staff roster is read on the global thread, the one
     * thread where it is consistent on Folia.
     */
    public void receive(RemoteStaffChat chat, String server) {
        Objects.requireNonNull(chat, "chat");
        Objects.requireNonNull(server, "server");
        if (!received.firstTime(chat.id())) {
            return;
        }
        scheduler.onGlobal(() -> channel.sendRemote(server, chat.from(), channel.onlineStaff(), chat.message()));
    }
}

package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.StaffChatFrame;
import com.uxplima.uxmessentials.staff.application.RemoteStaffChat;
import com.uxplima.uxmessentials.staff.application.SendStaffChat;
import com.uxplima.uxmessentials.staff.application.port.StaffChatNetwork;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link StaffChatNetwork} over the shared cross-server bus: a staff chat line leaves as a {@link StaffChatFrame},
 * and the frames peers send come back through {@link #onFrame}, which the wiring registers with the bus. That runs on
 * the bus's off-tick dispatch thread; {@link SendStaffChat} hops to the global thread before it reads the roster.
 *
 * <p>The use case is bound after construction because it holds this port itself.
 */
@NullMarked
public final class BusStaffChatNetwork implements StaffChatNetwork {

    private final BusPublisher publisher;
    private volatile @Nullable SendStaffChat staffChat;

    public BusStaffChatNetwork(BusPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    /** Hand inbound lines to {@code staffChat} from now on. */
    public void bind(SendStaffChat staffChat) {
        this.staffChat = Objects.requireNonNull(staffChat, "staffChat");
    }

    @Override
    public void publish(RemoteStaffChat chat) {
        Objects.requireNonNull(chat, "chat");
        publisher.publish(new StaffChatFrame(
                publisher.serverId(), chat.id(), chat.from().uuid(), chat.from().name(), chat.message()));
    }

    /** Register this with the bus registry: show a staff chat line from a peer to the staff here. */
    public void onFrame(NetworkMessage message) {
        SendStaffChat bound = staffChat;
        if (bound != null && message instanceof StaffChatFrame frame) {
            bound.receive(
                    new RemoteStaffChat(
                            frame.messageId(), new PlayerRef(frame.senderUuid(), frame.senderName()), frame.message()),
                    frame.originServer());
        }
    }
}

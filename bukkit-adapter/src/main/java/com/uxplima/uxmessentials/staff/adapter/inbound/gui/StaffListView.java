package com.uxplima.uxmessentials.staff.adapter.inbound.gui;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.port.StaffTeleport;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /stafflist}: a paginated grid of online staff — the holders of {@code uxmessentials.staff.member} the
 * looker can see (a vanished staffer they may not see is hidden). Clicking a head teleports to that staff member
 * as an admin teleport. An empty roster sends {@link StaffMessageKey#STAFF_LIST_EMPTY} rather than opening an
 * empty window. The shared {@link StaffTeleportPicker} owns the GUI build, the head buttons, the entity-thread
 * hops, and the teleport feedback. This is the GUI counterpart to the presence context's text {@code /staff}
 * roster — it agrees on who is staff via the same {@code uxmessentials.staff.member} marker.
 */
@NullMarked
public final class StaffListView extends StaffTeleportPicker {

    private static final String STAFF_MEMBER_NODE = "uxmessentials.staff.member";

    public StaffListView(
            Server server, Messages messages, MessageSink sink, Scheduler scheduler, StaffTeleport teleport) {
        super(server, messages, sink, scheduler, teleport);
    }

    @Override
    MessageKey titleKey() {
        return StaffMessageKey.STAFF_LIST_TITLE;
    }

    @Override
    List<Player> candidates(Player looker) {
        return server.getOnlinePlayers().stream()
                .filter(looker::canSee)
                .filter(online -> online.hasPermission(STAFF_MEMBER_NODE))
                .collect(Collectors.toList());
    }

    @Override
    void onOpen(Player looker, PlayerRef lookerRef) {
        List<Player> roster = candidates(looker);
        if (roster.isEmpty()) {
            sendChat(lookerRef, StaffMessageKey.STAFF_LIST_EMPTY);
            return;
        }
        buildAndOpen(looker, lookerRef, roster);
    }
}

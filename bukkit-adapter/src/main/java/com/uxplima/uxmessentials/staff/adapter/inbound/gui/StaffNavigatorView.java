package com.uxplima.uxmessentials.staff.adapter.inbound.gui;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.port.StaffTeleport;
import org.jspecify.annotations.NullMarked;

/**
 * The COMPASS gadget's navigator: a paginated grid of every online player the staff member can see (so a
 * vanished player they may not see is not listed), excluding themselves. Clicking a head teleports the staff
 * member onto that player as an admin teleport. The shared {@link StaffTeleportPicker} owns the GUI build, the
 * head buttons, the entity-thread hops, and the success/failure feedback.
 */
@NullMarked
public final class StaffNavigatorView extends StaffTeleportPicker {

    public StaffNavigatorView(
            Server server, Messages messages, MessageSink sink, Scheduler scheduler, StaffTeleport teleport) {
        super(server, messages, sink, scheduler, teleport);
    }

    @Override
    MessageKey titleKey() {
        return StaffMessageKey.STAFF_NAVIGATOR_TITLE;
    }

    @Override
    List<Player> candidates(Player looker) {
        return server.getOnlinePlayers().stream()
                .filter(online -> !online.getUniqueId().equals(looker.getUniqueId()))
                .filter(looker::canSee)
                .collect(Collectors.toList());
    }
}

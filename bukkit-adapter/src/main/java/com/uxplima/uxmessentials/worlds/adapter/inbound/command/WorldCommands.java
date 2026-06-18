package com.uxplima.uxmessentials.worlds.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.worlds.adapter.WorldsServices;
import org.jspecify.annotations.NullMarked;

/** Builds the realised worlds Brigadier commands from the started module's services. */
@NullMarked
public final class WorldCommands {

    private WorldCommands() {}

    public static List<CommandRegistration> all(WorldsServices services, Messages messages) {
        return List.of(new WorldCommand(services, messages), new WorldConfirmCommand(services, messages));
    }
}

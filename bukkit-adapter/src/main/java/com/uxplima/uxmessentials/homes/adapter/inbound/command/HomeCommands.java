package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import java.util.List;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the homes context's Brigadier command surface (docs/10-feature-modules.md §15.1) as
 * {@link CommandRegistration}s over the constructed {@link HomeServices}. Collected in one greppable table
 * so the literal/permission pairing matches the permissions reference and the kernel's
 * {@code HomeCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS} handler registers each.
 */
@NullMarked
public final class HomeCommands {

    private HomeCommands() {}

    /** Every homes command, in surface order. */
    public static List<CommandRegistration> all(
            HomeServices services, Messages messages, Supplier<ListDisplayMode> displayMode) {
        return List.of(
                new HomeCommand(services, messages),
                new SetHomeCommand(services, messages),
                new DelHomeCommand(services, messages),
                new HomesCommand(services, messages, displayMode),
                new RenameHomeCommand(services, messages),
                new MoveHomeCommand(services, messages),
                new SetMainHomeCommand(services, messages),
                new HomeAdminCommand(services, messages));
    }
}

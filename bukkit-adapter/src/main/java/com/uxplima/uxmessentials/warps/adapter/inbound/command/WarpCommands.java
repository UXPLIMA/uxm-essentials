package com.uxplima.uxmessentials.warps.adapter.inbound.command;

import java.util.List;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the warps context's Brigadier command surface (docs/10-feature-modules.md §15.2) as
 * {@link CommandRegistration}s over the constructed {@link WarpServices}. Collected in one greppable table
 * so the literal/permission pairing matches the permissions reference and the kernel's
 * {@code WarpCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS} handler registers each.
 */
@NullMarked
public final class WarpCommands {

    private WarpCommands() {}

    /** Every warps command, in surface order. */
    public static List<CommandRegistration> all(
            WarpServices services, Messages messages, Supplier<ListDisplayMode> displayMode) {
        return List.of(
                new WarpCommand(services, messages),
                new SetWarpCommand(services, messages),
                new DelWarpCommand(services, messages),
                new WarpsCommand(services, messages, displayMode),
                new WarpInfoCommand(services, messages),
                new MoveWarpCommand(services, messages));
    }
}

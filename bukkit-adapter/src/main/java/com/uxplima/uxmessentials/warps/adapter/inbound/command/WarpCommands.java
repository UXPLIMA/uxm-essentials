package com.uxplima.uxmessentials.warps.adapter.inbound.command;

import java.util.List;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the warps context's Brigadier command surface (docs/10-feature-modules.md §15.2) as a single
 * {@link CommandRegistration} over the constructed {@link WarpServices}. Everything a player does with server
 * warps hangs off one {@code /warp} command: {@code <name>} teleports, while {@code list}, {@code set},
 * {@code del}, {@code info}, {@code move} and the per-warp {@code lock}/{@code password}/{@code rate}/
 * {@code rating}/{@code edit} actions are Brigadier literals each gated by its own permission node via
 * {@code .requires(...)}. Collected here so the literal/permission pairing matches the permissions reference
 * and the kernel's {@code WarpCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS} handler
 * registers it. Mirrors the subcommand-tree idiom {@code /pwarp} and {@code /home} use.
 */
@NullMarked
public final class WarpCommands {

    private WarpCommands() {}

    /** Every warps command, in surface order. */
    public static List<CommandRegistration> all(
            WarpServices services, Messages messages, Supplier<ListDisplayMode> displayMode) {
        return List.of(new WarpCommand(services, messages, displayMode));
    }
}

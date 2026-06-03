package com.uxplima.uxmessentials.playerwarps.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the player-warps context's Brigadier command surface (docs/10-feature-modules.md §15.2) as
 * {@link CommandRegistration}s over the constructed {@link PlayerWarpServices}. Collected in one greppable
 * table so the literal/permission pairing matches the permissions reference and the kernel's
 * {@code PlayerWarpCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS} handler registers each.
 */
@NullMarked
public final class PlayerWarpCommands {

    private PlayerWarpCommands() {}

    /** Every player-warps command, in surface order. */
    public static List<CommandRegistration> all(PlayerWarpServices services, Messages messages) {
        return List.of(
                new PlayerWarpCommand(services, messages),
                new SetPlayerWarpCommand(services, messages),
                new DelPlayerWarpCommand(services, messages),
                new PlayerWarpsCommand(services, messages));
    }
}

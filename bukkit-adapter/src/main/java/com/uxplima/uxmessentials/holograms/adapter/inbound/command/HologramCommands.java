package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the holograms context's Brigadier command surface as {@link CommandRegistration}s over the
 * constructed {@link HologramServices}. The context publishes a single {@code /hologram} command with its
 * subcommands, matching the kernel's {@code HologramCommandSurface}; the plugin's
 * {@code LifecycleEvents.COMMANDS} handler registers it.
 */
@NullMarked
public final class HologramCommands {

    private HologramCommands() {}

    /** Every holograms command (one: {@code /hologram}). */
    public static List<CommandRegistration> all(HologramServices services, Messages messages) {
        return List.of(new HologramCommand(services, messages));
    }
}

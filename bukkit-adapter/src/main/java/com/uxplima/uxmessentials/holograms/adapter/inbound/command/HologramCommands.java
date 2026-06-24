package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.adapter.inbound.gui.HologramListMenu;
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

    /**
     * Every holograms command (one: {@code /hologram}). {@code hologramNames} is a side-effect-free, non-blocking
     * source of the current hologram names used to tab-complete every {@code name} argument across the surface;
     * {@code npcNames} is the same for the NPC argument of {@code /hologram linknpc}. {@code listMenu} is the
     * management-GUI list {@code /hologram} with no arguments opens for a holder of the GUI permission.
     */
    public static List<CommandRegistration> all(
            HologramServices services,
            Messages messages,
            Supplier<? extends Collection<String>> hologramNames,
            Supplier<? extends Collection<String>> npcNames,
            HologramListMenu listMenu) {
        return List.of(new HologramCommand(services, messages, hologramNames, npcNames, listMenu));
    }
}

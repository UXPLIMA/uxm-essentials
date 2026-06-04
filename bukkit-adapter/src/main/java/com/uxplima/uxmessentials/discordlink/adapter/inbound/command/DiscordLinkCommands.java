package com.uxplima.uxmessentials.discordlink.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.discordlink.adapter.DiscordLinkServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the discord-link context's Brigadier command surface as {@link CommandRegistration}s over the
 * constructed {@link DiscordLinkServices}. Collected in one greppable table so the literal/permission pairing
 * matches the permissions reference and the kernel's {@code DiscordLinkCommandSurface}; the plugin's
 * {@code LifecycleEvents.COMMANDS} handler registers each.
 */
@NullMarked
public final class DiscordLinkCommands {

    private DiscordLinkCommands() {}

    /** Every discord-link command, in surface order. */
    public static List<CommandRegistration> all(DiscordLinkServices services) {
        return List.of(new DiscordLinkCommand(services), new DiscordUnlinkCommand(services));
    }
}

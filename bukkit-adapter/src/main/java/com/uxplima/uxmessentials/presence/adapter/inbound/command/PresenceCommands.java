package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the presence context's Brigadier command surface (docs/10-feature-modules.md §15.8) as
 * {@link CommandRegistration}s over the constructed {@link PresenceServices}: {@code /afk [reason]},
 * {@code /vanish}, {@code /list}, {@code /realname <player>}, {@code /whois <player>}, {@code /gc}, and
 * {@code /staff}. Collected in one
 * greppable table so the literal/permission pairing matches the permissions
 * reference and the kernel's {@code PresenceCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS}
 * handler registers each.
 */
@NullMarked
public final class PresenceCommands {

    private PresenceCommands() {}

    /** Every presence command, in surface order. */
    public static List<CommandRegistration> all(PresenceServices services, Messages messages) {
        return List.of(
                new AfkCommand(services, messages),
                new VanishCommand(services, messages),
                new ListCommand(services, messages),
                new RealnameCommand(services, messages),
                new WhoisCommand(services, messages),
                new GcCommand(services, messages),
                new StaffCommand(services, messages));
    }
}

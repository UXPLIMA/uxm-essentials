package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.adapter.inbound.gui.OnlinePlayerListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Builds the presence context's Brigadier command surface (docs/10-feature-modules.md §15.8) as
 * {@link CommandRegistration}s over the constructed {@link PresenceServices}: {@code /afk [reason]},
 * {@code /list}, {@code /realname <player>}, {@code /whois <player>}, {@code /gc}, and
 * {@code /staff}. Collected in one
 * greppable table so the literal/permission pairing matches the permissions
 * reference and the kernel's {@code PresenceCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS}
 * handler registers each. {@code /vanish} moved to the dedicated {@code vanish} context.
 */
@NullMarked
public final class PresenceCommands {

    private PresenceCommands() {}

    /** Every presence command, in surface order. */
    public static List<CommandRegistration> all(
            PresenceServices services,
            Messages messages,
            Scheduler scheduler,
            @Nullable OnlinePlayerListView listView) {
        return List.of(
                new AfkCommand(services, messages, scheduler),
                new ListCommand(services, messages, scheduler, listView),
                new RealnameCommand(services, messages, scheduler),
                new NickCommand(services, messages, scheduler),
                new WhoisCommand(services, messages, scheduler),
                new GcCommand(services, messages, scheduler),
                new StaffCommand(services, messages, scheduler));
    }
}

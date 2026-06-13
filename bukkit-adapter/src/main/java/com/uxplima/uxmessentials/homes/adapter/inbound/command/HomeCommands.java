package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the homes context's Brigadier command surface (docs/10-feature-modules.md §15.1) as
 * {@link CommandRegistration}s over the constructed {@link HomeServices}. The slot-grid GUI is the player's one
 * entry point for their own homes, so {@code /home} opens the grid and {@code /homeadmin} is staff management;
 * everything a player used to do through {@code /sethome}, {@code /delhome}, {@code /renamehome},
 * {@code /movehome}, {@code /setmainhome}, and {@code /homes} now happens inside the grid. The public/visit
 * surface adds {@code /visit} (teleport to another player's home), {@code /invite}, and {@code /uninvite} (grant
 * and revoke access to one of your private homes). Collected in one greppable table so the literal/permission
 * pairing matches the permissions reference and the kernel's {@code HomeCommandSurface}.
 */
@NullMarked
public final class HomeCommands {

    private HomeCommands() {}

    /** Every homes command, in surface order. */
    public static List<CommandRegistration> all(HomeServices services, Messages messages, Scheduler scheduler) {
        return List.of(
                new HomeCommand(services, messages),
                new HomeAdminCommand(services, messages, scheduler),
                new VisitCommand(services, messages, scheduler),
                new InviteCommand(services, messages, scheduler),
                new UninviteCommand(services, messages, scheduler));
    }
}

package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the homes context's Brigadier command surface (docs/10-feature-modules.md §15.1) as a single
 * {@link CommandRegistration} over the constructed {@link HomeServices}. Everything a player does with homes
 * hangs off one {@code /home} command: the no-arg invocation opens the slot grid (the player's own homes;
 * {@code /sethome}, {@code /delhome}, {@code /renamehome}, {@code /movehome}, {@code /setmainhome} and
 * {@code /homes} all happen inside it), while {@code visit}, {@code invite}, {@code uninvite} and the
 * {@code admin} subtree are Brigadier literals each gated by its own permission node. Mirrors the
 * subcommand-tree idiom {@code /pwarp} uses.
 */
@NullMarked
public final class HomeCommands {

    private HomeCommands() {}

    /** Every homes command, in surface order. */
    public static List<CommandRegistration> all(HomeServices services, Messages messages, Scheduler scheduler) {
        return List.of(new HomeCommand(services, messages, scheduler));
    }
}

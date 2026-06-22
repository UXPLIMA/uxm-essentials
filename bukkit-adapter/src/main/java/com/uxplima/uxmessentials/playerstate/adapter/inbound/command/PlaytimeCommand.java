package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /playtime [player]} ({@code uxmessentials.playtime.use}): show a player's playtime breakdown — active
 * (non-AFK) and AFK time across today / last 7 days / last 30 days / all time, read from the DB-backed
 * {@code ShowPlaytime} use case, plus a lifetime continuity line. The {@code .others} target is gated by the
 * shared {@code uxmessentials.playerstate.others} node.
 *
 * <p>{@code /playtime reset [player]} ({@code uxmessentials.playtime.reset}): wipe a player's tracked playtime.
 * The reset capability itself is the {@code uxmessentials.playtime.reset} admin node (resetting even your own
 * tracked time is an administrative action, off by default); resetting <em>another</em> player additionally
 * requires the shared {@code uxmessentials.playerstate.others} node, the same target gate every other
 * self/other playerstate command uses.
 */
@NullMarked
public final class PlaytimeCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.playtime.use";
    private static final String RESET_PERMISSION = "uxmessentials.playtime.reset";

    public PlaytimeCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("playtime")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::show)
                .then(Commands.literal("reset")
                        .requires(src -> src.getSender().hasPermission(RESET_PERMISSION))
                        .executes(this::reset)
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(this::reset)))
                .then(Commands.argument("player", ArgumentTypes.player()).executes(this::show))
                .build();
    }

    @Override
    public String description() {
        return "Show or reset a player's playtime breakdown.";
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.showPlaytime().showFor(ref(sender), target.get());
        return Command.SINGLE_SUCCESS;
    }

    private int reset(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.resetPlaytime().resetFor(ref(sender), target.get());
        return Command.SINGLE_SUCCESS;
    }
}

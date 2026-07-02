package com.uxplima.uxmessentials.poses.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.poses.application.TogglePlayerSit;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /poses}: the poses context's own root command. Its {@code toggle} subcommand
 * ({@code uxmessentials.poses.toggle}) flips whether other players may sit on you, and the bare {@code /poses}
 * prints a short usage line pointing at it. Phase 6 turns the bare root into the settings/status GUI; for now it
 * only hosts {@code toggle}.
 */
@NullMarked
public final class PosesCommand implements CommandRegistration {

    private static final String TOGGLE_PERMISSION = "uxmessentials.poses.toggle";

    private final TogglePlayerSit togglePlayerSit;
    private final CommandFeedback feedback;

    public PosesCommand(TogglePlayerSit togglePlayerSit, Messages messages) {
        this.togglePlayerSit = Objects.requireNonNull(togglePlayerSit, "togglePlayerSit");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("poses")
                .then(Commands.literal("toggle")
                        .requires(src -> src.getSender().hasPermission(TOGGLE_PERMISSION))
                        .executes(this::toggle))
                .executes(this::usage)
                .build();
    }

    @Override
    public String description() {
        return "Poses settings — /poses toggle to allow or refuse others sitting on you.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        boolean nowAllows = togglePlayerSit.toggle(BukkitRefs.toRef(player));
        feedback.send(
                player,
                nowAllows ? PosesMessageKey.POSES_PLAYERSIT_NOW_ALLOWED : PosesMessageKey.POSES_PLAYERSIT_NOW_REFUSED);
        return Command.SINGLE_SUCCESS;
    }

    private int usage(CommandContext<CommandSourceStack> ctx) {
        feedback.send(ctx.getSource().getSender(), PosesMessageKey.POSES_USAGE);
        return Command.SINGLE_SUCCESS;
    }
}

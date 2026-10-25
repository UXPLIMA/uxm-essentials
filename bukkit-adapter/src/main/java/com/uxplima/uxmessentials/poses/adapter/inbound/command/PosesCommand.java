package com.uxplima.uxmessentials.poses.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.poses.adapter.inbound.gui.PosesSettingsView;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.poses.application.RevealPoser;
import com.uxplima.uxmessentials.poses.application.TogglePlayerSit;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /poses}: the poses context's own root command. A bare {@code /poses} (and the explicit {@code /poses gui}
 * subcommand) opens the personal settings/status panel, the current-pose status and the player-sit opt-out, gated
 * by the self-service {@code uxmessentials.poses.gui}; a non-player sender who cannot open a GUI gets the usage line
 * instead. The {@code toggle} subcommand ({@code uxmessentials.poses.toggle}) flips the opt-out from chat, the same
 * flip the panel's button makes. The {@code reveal <player>} subcommand ({@code uxmessentials.poses.reveal}, an
 * operator's) gives a player their body back when a pose left them unseen.
 */
@NullMarked
public final class PosesCommand implements CommandRegistration {

    private static final String TOGGLE_PERMISSION = "uxmessentials.poses.toggle";
    private static final String GUI_PERMISSION = "uxmessentials.poses.gui";
    private static final String REVEAL_PERMISSION = "uxmessentials.poses.reveal";
    private static final String TARGET = "player";

    private final TogglePlayerSit togglePlayerSit;
    private final PosesSettingsView settingsView;
    private final RevealPoser revealPoser;
    private final Scheduler scheduler;
    private final CommandFeedback feedback;

    public PosesCommand(
            TogglePlayerSit togglePlayerSit,
            PosesSettingsView settingsView,
            RevealPoser revealPoser,
            Scheduler scheduler,
            Messages messages) {
        this.togglePlayerSit = Objects.requireNonNull(togglePlayerSit, "togglePlayerSit");
        this.settingsView = Objects.requireNonNull(settingsView, "settingsView");
        this.revealPoser = Objects.requireNonNull(revealPoser, "revealPoser");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("poses")
                // Hide the whole /poses tree from a player with no poses access; either self-service node (its bare
                // GUI or the toggle) keeps it visible, so a player holding only one still sees the command.
                .requires(src -> src.getSender().hasPermission(GUI_PERMISSION)
                        || src.getSender().hasPermission(TOGGLE_PERMISSION)
                        || src.getSender().hasPermission(REVEAL_PERMISSION))
                .then(Commands.literal("toggle")
                        .requires(src -> src.getSender().hasPermission(TOGGLE_PERMISSION))
                        .executes(this::toggle))
                .then(Commands.literal("gui")
                        .requires(src -> src.getSender().hasPermission(GUI_PERMISSION))
                        .executes(this::openGui))
                .then(Commands.literal("reveal")
                        .requires(src -> src.getSender().hasPermission(REVEAL_PERMISSION))
                        .then(Commands.argument(TARGET, ArgumentTypes.player()).executes(this::reveal)))
                .executes(this::open)
                .build();
    }

    @Override
    public String description() {
        return "Open your poses panel; /poses toggle to allow or refuse others sitting on you; /poses reveal <player>"
                + " gives back a body a pose left unseen.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
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

    /** The bare {@code /poses}: open the panel for a player, or fall back to the usage line for the console. */
    private int open(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        if (!(sender instanceof Player player) || !player.hasPermission(GUI_PERMISSION)) {
            feedback.send(sender, PosesMessageKey.POSES_USAGE);
            return Command.SINGLE_SUCCESS;
        }
        settingsView.open(player, BukkitRefs.toRef(player));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Give the named player their body back, on the thread that owns them, and tell the sender what it did. The
     * console may run it, which is how an operator reaches a player who is unseen and cannot be found to click.
     */
    private int reveal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = Sender.audience(ctx.getSource());
        List<Player> named =
                ctx.getArgument(TARGET, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        if (named.isEmpty()) {
            String typed = ctx.getInput().substring(ctx.getInput().lastIndexOf(' ') + 1);
            feedback.send(sender, SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of(TARGET, typed));
            return 0;
        }
        Player target = named.get(0);
        PlayerRef who = BukkitRefs.toRef(target);
        scheduler.onEntity(who, () -> {
            RevealPoser.Outcome outcome = revealPoser.reveal(who);
            feedback.send(
                    sender,
                    outcome == RevealPoser.Outcome.REVEALED
                            ? PosesMessageKey.POSES_REVEALED
                            : PosesMessageKey.POSES_REVEAL_NOTHING,
                    Map.of(TARGET, who.name()));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int openGui(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        settingsView.open(player, BukkitRefs.toRef(player));
        return Command.SINGLE_SUCCESS;
    }
}

package com.uxplima.uxmessentials.poses.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.poses.application.StartPose;
import com.uxplima.uxmessentials.poses.application.StartPose.PoseOutcome;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * One free-pose command — {@code /lay}, {@code /bellyflop}, or {@code /spin} — gated on its own
 * {@code uxmessentials.<pose>.use} node. A free pose is always struck where the player stands, so this handler
 * resolves nothing but the player's own feet and hands the pose to {@link StartPose}, which owns the feature gate,
 * the region gate, the one-session invariant, and the anchor. The three commands share this class, each constructed
 * with its literal, permission, {@link PoseType}, and the feedback line for a successful start.
 */
@NullMarked
public final class PoseCommand implements CommandRegistration {

    private final String literal;
    private final String permission;
    private final PoseType pose;
    private final MessageKey startedKey;
    private final String description;
    private final StartPose startPose;
    private final CommandFeedback feedback;

    public PoseCommand(
            String literal,
            String permission,
            PoseType pose,
            MessageKey startedKey,
            String description,
            StartPose startPose,
            Messages messages) {
        this.literal = Objects.requireNonNull(literal, "literal");
        this.permission = Objects.requireNonNull(permission, "permission");
        this.pose = Objects.requireNonNull(pose, "pose");
        this.startedKey = Objects.requireNonNull(startedKey, "startedKey");
        this.description = Objects.requireNonNull(description, "description");
        this.startPose = Objects.requireNonNull(startPose, "startPose");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(permission))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return description;
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        Location location = Objects.requireNonNull(player.getLocation(), "player location");
        Position feet = BukkitRefs.toPosition(location);
        PoseOutcome outcome = startPose.start(BukkitRefs.toRef(player), pose, feet, feet.yaw());
        feedback.send(player, feedbackFor(outcome));
        return Command.SINGLE_SUCCESS;
    }

    private MessageKey feedbackFor(PoseOutcome outcome) {
        return switch (outcome) {
            case STARTED -> startedKey;
            case DISABLED -> PosesMessageKey.POSES_POSE_DISABLED;
            case DENIED_REGION -> PosesMessageKey.POSES_CANNOT_HERE;
            case ALREADY_POSING -> PosesMessageKey.POSES_ALREADY_POSING;
        };
    }
}

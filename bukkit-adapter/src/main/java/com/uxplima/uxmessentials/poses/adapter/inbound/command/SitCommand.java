package com.uxplima.uxmessentials.poses.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.poses.adapter.inbound.SeatGeometry;
import com.uxplima.uxmessentials.poses.adapter.inbound.SeatGeometry.SeatTarget;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.poses.application.StartSit;
import com.uxplima.uxmessentials.poses.application.StartSit.SitOutcome;
import com.uxplima.uxmessentials.poses.domain.SittableBlocks;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /sit} ({@code uxmessentials.sit.use}): sit down where you stand, or on the sittable block you look at
 * within reach. The {@link StartSit} use case owns the region gate, the one-session invariant, and the seat; this
 * handler only resolves <em>which</em> block to sit on — the {@link SeatGeometry} of the looked-at block when
 * {@code sit-on-blocks} is on and it is sittable, otherwise the player's own feet — and renders the outcome.
 */
@NullMarked
public final class SitCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.sit.use";

    private final StartSit startSit;
    private final SittableBlocks sittableBlocks;
    private final CommandFeedback feedback;
    private final boolean sitOnBlocks;
    private final int reach;

    public SitCommand(
            StartSit startSit,
            SittableBlocks sittableBlocks,
            Messages messages,
            boolean sitOnBlocks,
            double maxDistance) {
        this.startSit = Objects.requireNonNull(startSit, "startSit");
        this.sittableBlocks = Objects.requireNonNull(sittableBlocks, "sittableBlocks");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
        this.sitOnBlocks = sitOnBlocks;
        this.reach = Math.max(1, (int) Math.ceil(maxDistance));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("sit")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::sit)
                .build();
    }

    @Override
    public String description() {
        return "Sit down where you stand or on the block you're looking at.";
    }

    private int sit(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        Spot spot = resolveSpot(player);
        SitOutcome outcome = startSit.start(BukkitRefs.toRef(player), spot.position(), spot.yaw(), spot.inPlace());
        feedback.send(player, feedbackFor(outcome));
        return Command.SINGLE_SUCCESS;
    }

    /** Where to sit: the looked-at sittable block when offered and in reach, otherwise the player's own feet. */
    private Spot resolveSpot(Player player) {
        Location location = Objects.requireNonNull(player.getLocation(), "player location");
        if (sitOnBlocks) {
            Block target = player.getTargetBlockExact(reach);
            if (target != null) {
                Optional<SeatTarget> seat = SeatGeometry.forBlock(target, sittableBlocks, location.getYaw());
                if (seat.isPresent()) {
                    return new Spot(seat.get().position(), seat.get().yaw(), false);
                }
            }
        }
        Position feet = BukkitRefs.toPosition(location);
        return new Spot(feet, feet.yaw(), true);
    }

    private static MessageKey feedbackFor(SitOutcome outcome) {
        return switch (outcome) {
            case STARTED -> PosesMessageKey.POSES_SITTING;
            case DISABLED -> PosesMessageKey.POSES_SIT_DISABLED;
            case DENIED_REGION -> PosesMessageKey.POSES_CANNOT_HERE;
            case ALREADY_POSING -> PosesMessageKey.POSES_ALREADY_POSING;
        };
    }

    private record Spot(Position position, float yaw, boolean inPlace) {}
}

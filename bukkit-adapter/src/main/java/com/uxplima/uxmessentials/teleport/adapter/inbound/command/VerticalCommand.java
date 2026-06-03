package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.NullMarked;

/**
 * The vertical / line-of-sight positional verbs — {@code /top}, {@code /bottom}, {@code /jump},
 * {@code /up}, {@code /down} — sharing one node builder and differing only by {@link Kind}. Each resolves
 * a destination from the player's column or line of sight on the region thread, then issues an instant hop
 * through the async executor. {@code /jump} and {@code /down} report "no target block" when nothing is in
 * sight (or no solid block lies below the player's feet).
 */
@NullMarked
public final class VerticalCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.tp.vertical";
    private static final int JUMP_REACH = 64;

    /** Which vertical verb this instance binds. */
    public enum Kind {
        TOP,
        BOTTOM,
        JUMP,
        UP,
        DOWN
    }

    private final String literal;
    private final String description;
    private final Kind kind;

    public VerticalCommand(
            TeleportServices services, Messages messages, String literal, String description, Kind kind) {
        super(services, messages);
        this.literal = Objects.requireNonNull(literal, "literal");
        this.description = Objects.requireNonNull(description, "description");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        var node = Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, 1));
        if (kind == Kind.UP) {
            node.then(Commands.argument("blocks", IntegerArgumentType.integer(1, 256))
                    .executes(ctx -> run(ctx, ctx.getArgument("blocks", Integer.class))));
        }
        return node.build();
    }

    @Override
    public String description() {
        return description;
    }

    private int run(CommandContext<CommandSourceStack> ctx, int blocks) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Position> target = resolve(sender, blocks);
        if (target.isEmpty()) {
            services.notifier().send(ref(sender), TeleportMessageKey.TP_NO_TARGET_BLOCK);
            return 0;
        }
        services.executor().teleport(ref(sender), Destination.at(target.get()), TeleportKind.POSITIONAL);
        return Command.SINGLE_SUCCESS;
    }

    private Optional<Position> resolve(Player sender, int blocks) {
        Location origin = TeleportRefs.location(sender);
        return switch (kind) {
            case TOP -> Optional.of(at(origin, sender.getWorld().getHighestBlockYAt(origin) + 1));
            case BOTTOM -> Optional.of(at(origin, sender.getWorld().getMinHeight() + 1));
            case UP -> Optional.of(at(origin, origin.getBlockY() + blocks));
            case JUMP -> jumpTarget(sender, origin);
            case DOWN -> downTarget(sender, origin);
        };
    }

    private static Optional<Position> downTarget(Player sender, Location origin) {
        World world = sender.getWorld();
        int x = origin.getBlockX();
        int z = origin.getBlockZ();
        for (int y = origin.getBlockY() - 1; y >= world.getMinHeight(); y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return Optional.of(at(origin, y + 1));
            }
        }
        return Optional.empty();
    }

    private static Position at(Location origin, int y) {
        Position current = BukkitRefs.toPosition(origin);
        return new Position(current.world(), current.x(), y, current.z(), current.yaw(), current.pitch());
    }

    private static Optional<Position> jumpTarget(Player sender, Location origin) {
        World world = sender.getWorld();
        RayTraceResult trace = world.rayTraceBlocks(
                sender.getEyeLocation(), sender.getEyeLocation().getDirection(), JUMP_REACH);
        Block hit = trace == null ? null : trace.getHitBlock();
        return Optional.ofNullable(hit).map(block -> standingOn(origin, block));
    }

    private static Position standingOn(Location origin, Block block) {
        Position eye = BukkitRefs.toPosition(origin);
        return new Position(
                BukkitRefs.toRef(block.getWorld()),
                block.getX() + 0.5,
                block.getY() + 1.0,
                block.getZ() + 0.5,
                eye.yaw(),
                eye.pitch());
    }
}

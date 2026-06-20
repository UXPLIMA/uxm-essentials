package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tree <type>}: grow a tree of the named {@link TreeType} one block above whatever the caller is
 * looking at. An admin-fun verb (audit-logged) — a quick terrain tool that is easy to abuse, so each grow is
 * recorded with actor and tree type. An unrecognised type answers {@link ItemworldMessageKey#TREE_UNKNOWN_TYPE}
 * and nothing in reach answers {@link ItemworldMessageKey#TREE_NO_TARGET}; both leave the world untouched.
 *
 * <p>Type matching is forgiving: the argument is lower-cased and stripped of underscores before comparison with
 * the enum names, and the bare word {@code jungle} resolves to {@link TreeType#SMALL_JUNGLE} (so
 * {@code /tree jungle} grows the smaller sapling rather than the 2x2 variant).
 *
 * <p>Growing mutates the world, so it runs on the caller's region thread through the kernel {@code Scheduler};
 * the target location and type name are captured before scheduling for the reply and the audit line.
 */
@NullMarked
public final class TreeCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.tree.use";
    private static final int REACH = 64;

    public TreeCommand(ItemworldServices services) {
        super(services, "tree", SubFeatureGroup.ADMIN_FUN, "Generate a tree where you are looking.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("type", StringArgumentType.word()).executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player self = player(ctx);
        if (self == null) {
            return Command.SINGLE_SUCCESS;
        }
        String arg = StringArgumentType.getString(ctx, "type");
        Optional<TreeType> resolved = resolve(arg);
        if (resolved.isEmpty()) {
            reply(ctx, ItemworldMessageKey.TREE_UNKNOWN_TYPE, Map.of("type", arg));
            return Command.SINGLE_SUCCESS;
        }
        Block target = self.getTargetBlockExact(REACH);
        if (target == null) {
            reply(ctx, ItemworldMessageKey.TREE_NO_TARGET);
            return Command.SINGLE_SUCCESS;
        }
        grow(ctx, ref(self), target.getLocation().add(0, 1, 0), resolved.get());
        return Command.SINGLE_SUCCESS;
    }

    private void grow(CommandContext<CommandSourceStack> ctx, PlayerRef actor, Location loc, TreeType type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        services.kernel().scheduler().onEntity(actor, () -> {
            World world = loc.getWorld();
            // The Random-seeded RegionAccessor overload, not the deprecated World#generateTree(Location, TreeType).
            boolean ok = world != null && world.generateTree(loc, ThreadLocalRandom.current(), type);
            if (ok) {
                reply(ctx, ItemworldMessageKey.TREE_SPAWNED, Map.of("type", name));
                services.audit().grewTree(actor, name);
            } else {
                reply(ctx, ItemworldMessageKey.TREE_FAILED, Map.of("type", name));
            }
        });
    }

    /** Resolve the friendly argument to a {@link TreeType}, with the {@code jungle} convenience. */
    private static Optional<TreeType> resolve(String arg) {
        Objects.requireNonNull(arg, "arg");
        String normalised = arg.toLowerCase(Locale.ROOT).replace("_", "");
        if (normalised.equals("jungle")) {
            return Optional.of(TreeType.SMALL_JUNGLE);
        }
        for (TreeType type : TreeType.values()) {
            if (type.name().toLowerCase(Locale.ROOT).replace("_", "").equals(normalised)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}

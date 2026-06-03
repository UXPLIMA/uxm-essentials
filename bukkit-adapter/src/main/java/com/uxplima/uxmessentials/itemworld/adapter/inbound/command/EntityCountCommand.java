package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.EnumMap;
import java.util.Map;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /entitycount [radius]}: tally the entities near the actor grouped by {@link EntityType}, for lag
 * diagnosis before {@code /butcher} or {@code /killall}. Distinct from {@code /near} (players), the purge
 * family (delete), and {@code /killall} (world-wide): this only reads. The scan is region-bound, so it runs on
 * the actor's region thread through the kernel {@code Scheduler}, and the radius is clamped to a sane maximum so
 * the bounded scan stays within the command's ms budget.
 *
 * <p>An empty area replies {@link ItemworldMessageKey#ENTITYCOUNT_NONE}; otherwise a
 * {@link ItemworldMessageKey#ENTITYCOUNT_HEADER} carrying the total is followed by one
 * {@link ItemworldMessageKey#ENTITYCOUNT_ENTRY} per type, ordered by count descending.
 */
@NullMarked
public final class EntityCountCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.entitycount.use";
    private static final int DEFAULT_RADIUS = 64;
    private static final int MAX_RADIUS = 256;

    public EntityCountCommand(ItemworldServices services) {
        super(services, "entitycount", SubFeatureGroup.MOB_ENTITY, "Count nearby entities by type.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, DEFAULT_RADIUS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, int requested) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        int radius = Math.min(requested, MAX_RADIUS);
        services.kernel().scheduler().onEntity(ref(player), () -> count(ctx, player, radius));
        return Command.SINGLE_SUCCESS;
    }

    private void count(CommandContext<CommandSourceStack> ctx, Player player, int radius) {
        Map<EntityType, Integer> tally = new EnumMap<>(EntityType.class);
        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            tally.merge(nearby.getType(), 1, Integer::sum);
        }
        if (tally.isEmpty()) {
            reply(ctx, ItemworldMessageKey.ENTITYCOUNT_NONE, Map.of("radius", String.valueOf(radius)));
            return;
        }
        int total = tally.values().stream().mapToInt(Integer::intValue).sum();
        reply(
                ctx,
                ItemworldMessageKey.ENTITYCOUNT_HEADER,
                Map.of("total", String.valueOf(total), "radius", String.valueOf(radius)));
        tally.entrySet().stream()
                .sorted(Map.Entry.<EntityType, Integer>comparingByValue().reversed())
                .forEach(entry -> reply(
                        ctx,
                        ItemworldMessageKey.ENTITYCOUNT_ENTRY,
                        Map.of("type", entry.getKey().getKey().toString(), "count", String.valueOf(entry.getValue()))));
    }
}

package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.EntityCountListView;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
 *
 * <p>With its catalog {@code gui} flag on, bare {@code /entitycount} (and {@code /entitycount <radius>}) opens the
 * {@link EntityCountListView} grid instead — the same tally, one spawn-egg icon per type sorted by count. The
 * {@link #runGui} opener installed on the bare root by the {@code GuiRootBinding} runs the same region-bound scan
 * on the actor's entity thread, then hands the sorted tally to the view; a console has no inventory and falls back
 * to the chat read-out. With the flag off the bare root keeps the chat listing.
 */
@NullMarked
public final class EntityCountCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.entitycount.use";
    private static final int DEFAULT_RADIUS = 64;
    private static final int MAX_RADIUS = 256;

    private final @Nullable EntityCountListView listView;

    public EntityCountCommand(ItemworldServices services) {
        this(services, null);
    }

    public EntityCountCommand(ItemworldServices services, @Nullable EntityCountListView listView) {
        super(services, "entitycount", SubFeatureGroup.MOB_ENTITY, "Count nearby entities by type.");
        this.listView = listView;
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

    /**
     * Bare {@code /entitycount} opens the tally menu when the command's catalog {@code gui} flag is on, scanning at
     * the default radius; with it off the bare root falls back to the chat read-out. Empty when no view was wired.
     */
    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        return listView == null ? Optional.empty() : Optional.of(ctx -> runGui(ctx, DEFAULT_RADIUS));
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

    /**
     * Bare {@code /entitycount} with gui on: run the same region-bound scan on the actor's entity thread, then open
     * the grid. A console has no inventory, so it falls back to the chat read-out.
     */
    private int runGui(CommandContext<CommandSourceStack> ctx, int requested) {
        EntityCountListView view = listView;
        if (view == null) {
            return run(ctx, requested);
        }
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        int radius = Math.min(requested, MAX_RADIUS);
        services.kernel().scheduler().onEntity(ref(player), () -> countGui(view, player, radius));
        return Command.SINGLE_SUCCESS;
    }

    private void count(CommandContext<CommandSourceStack> ctx, Player player, int radius) {
        Map<EntityType, Integer> tally = tally(player, radius);
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

    private void countGui(EntityCountListView view, Player player, int radius) {
        List<EntityCountListView.Entry> sorted = tally(player, radius).entrySet().stream()
                .sorted(Map.Entry.<EntityType, Integer>comparingByValue().reversed())
                .map(e -> new EntityCountListView.Entry(e.getKey(), e.getValue()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        view.open(player, ref(player), sorted, radius);
    }

    /** Tally the entities within {@code radius} of {@code player} by type; runs on the actor's region thread. */
    private static Map<EntityType, Integer> tally(Player player, int radius) {
        Map<EntityType, Integer> tally = new EnumMap<>(EntityType.class);
        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            tally.merge(nearby.getType(), 1, Integer::sum);
        }
        return tally;
    }
}

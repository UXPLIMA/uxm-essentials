package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * One virtual-workstation command — {@code /anvil}, {@code /workbench} ({@code /craft}), {@code /enderchest}
 * ({@code /echest}), {@code /grindstone}, {@code /cartography}, {@code /loom}, {@code /smithingtable},
 * {@code /stonecutter}, {@code /furnace} — parameterised by its {@link Workstation}. Opening an inventory view
 * is an entity-bound operation, so the open is scheduled on the player's region thread through the kernel
 * {@code Scheduler} and reported through {@link ItemworldMessageKey#WORKSTATION_OPENED}. The whole group is
 * gated by the {@code workstations} sub-feature flag and each command by its own per-command disable.
 */
@NullMarked
public final class WorkstationCommand extends ItemworldCommandSupport implements CommandRegistration {

    private final Workstation station;

    public WorkstationCommand(Workstation station, ItemworldServices services) {
        super(
                services,
                station.literal(),
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual " + station.displayName() + ".");
        this.station = station;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(station.permission()))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public List<String> aliases() {
        return station.aliases();
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        services.kernel().scheduler().onEntity(ref(player), () -> {
            station.open(player);
            reply(ctx, ItemworldMessageKey.WORKSTATION_OPENED, Map.of("station", station.displayName()));
        });
        return Command.SINGLE_SUCCESS;
    }
}

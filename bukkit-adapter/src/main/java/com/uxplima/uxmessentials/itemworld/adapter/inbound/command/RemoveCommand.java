package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.time.Instant;
import java.util.Map;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitEntityPurger;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.application.PurgePolicy;
import com.uxplima.uxmessentials.itemworld.domain.ItemWorldError;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.itemworld.domain.event.EntitiesPurged;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /remove <type> [radius]}: remove a named entity type within a radius around the actor. An entity-purge
 * verb (audit-logged). A blank type is rejected by the domain {@link PurgePolicy}
 * ({@link ItemworldMessageKey#SPAWNMOB_UNKNOWN}); the radius is clamped to {@code purge-max-radius}. Players and
 * tamed pets are never swept.
 *
 * <p>The sweep is region-bound, so it runs on the actor's region thread through the kernel {@code Scheduler};
 * the count removed is reported through {@link ItemworldMessageKey#REMOVE_DONE}, audited, and published as an
 * {@link EntitiesPurged} domain event.
 */
@NullMarked
public final class RemoveCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.remove.use";
    private static final int DEFAULT_RADIUS = 64;

    private final PurgePolicy policy;

    public RemoveCommand(ItemworldServices services, PurgePolicy policy) {
        super(services, "remove", SubFeatureGroup.MOB_ENTITY, "Remove entities by type.");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("type", StringArgumentType.word())
                        .executes(ctx -> run(ctx, DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, int radius) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        String type = ctx.getArgument("type", String.class);
        Result<PurgeSelection, ItemWorldError> selection = policy.remove(type, radius);
        if (selection.isErr()) {
            reply(ctx, ItemworldMessageKey.SPAWNMOB_UNKNOWN, Map.of("mob", type));
            return Command.SINGLE_SUCCESS;
        }
        sweep(ctx, player, selection.orElseThrow());
        return Command.SINGLE_SUCCESS;
    }

    private void sweep(CommandContext<CommandSourceStack> ctx, Player player, PurgeSelection selection) {
        PlayerRef actor = ref(player);
        String type = selection.typeId().orElse("all");
        services.kernel().scheduler().onEntity(actor, () -> {
            int removed = BukkitEntityPurger.purge(player, selection);
            reply(ctx, ItemworldMessageKey.REMOVE_DONE, Map.of("count", String.valueOf(removed), "type", type));
            services.audit().removed(actor, selection, removed);
            services.kernel()
                    .events()
                    .publish(new EntitiesPurged(
                            actor, selection, BukkitRefs.toRef(player.getWorld()), removed, Instant.now()));
        });
    }
}

package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitEntityPurger;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.application.PurgePolicy;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.itemworld.domain.event.EntitiesPurged;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /killall [type]}: purge entities world-wide — a single named type, or every removable entity when no
 * type is given. An entity-purge verb (audit-logged). The selection is shaped by the domain {@link PurgePolicy}
 * ({@code killall} with a blank type sweeps all entities, a named type sweeps that type only); players and tamed
 * pets are never swept.
 *
 * <p>A world sweep is region-sensitive, so it runs on the actor's region thread through the kernel
 * {@code Scheduler}; the count removed is reported through {@link ItemworldMessageKey#KILLALL_DONE}, audited, and
 * published as an {@link EntitiesPurged} domain event.
 */
@NullMarked
public final class KillAllCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.killall.use";

    private final PurgePolicy policy;

    public KillAllCommand(ItemworldServices services, PurgePolicy policy) {
        super(services, "killall", SubFeatureGroup.MOB_ENTITY, "Purge entities world-wide.");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, ""))
                .then(Commands.argument("type", StringArgumentType.word())
                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "type"))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, String type) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PurgeSelection selection = policy.killAll(type);
        sweep(ctx, player, selection);
        return Command.SINGLE_SUCCESS;
    }

    private void sweep(CommandContext<CommandSourceStack> ctx, Player player, PurgeSelection selection) {
        PlayerRef actor = ref(player);
        Optional<String> type = selection.typeId();
        services.kernel().scheduler().onEntity(actor, () -> {
            int removed = BukkitEntityPurger.purge(player, selection);
            reply(
                    ctx,
                    ItemworldMessageKey.KILLALL_DONE,
                    Map.of("count", String.valueOf(removed), "type", type.orElse("all")));
            services.audit().killedAll(actor, selection, removed);
            services.kernel()
                    .events()
                    .publish(new EntitiesPurged(
                            actor, selection, BukkitRefs.toRef(player.getWorld()), removed, Instant.now()));
        });
    }
}

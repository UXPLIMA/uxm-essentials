package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitEntityResolver;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /spawner <type>}: set the mob type of the spawner the player is looking at. An abusable verb
 * (audit-logged): retyping a spawner to a high-value mob is an economy lever, so the change is recorded. The
 * target block is the one in the player's line of sight; it must be a {@link CreatureSpawner} (else
 * {@link ItemworldMessageKey#SPAWNER_NOT_A_SPAWNER}). The mob id is validated against the live registry at the
 * boundary (else {@link ItemworldMessageKey#SPAWNMOB_UNKNOWN}).
 *
 * <p>Reading and writing the block state is region-bound, so it runs on the block's region thread through the
 * kernel {@code Scheduler}; the change is reported through {@link ItemworldMessageKey#SPAWNER_SET} and audited.
 */
@NullMarked
public final class SpawnerCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.spawner.use";
    private static final int REACH = 8;

    public SpawnerCommand(ItemworldServices services) {
        super(services, "spawner", SubFeatureGroup.MOB_ENTITY, "Set a spawner's mob type.");
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

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        String rawType = ctx.getArgument("type", String.class);
        String typeId = rawType.trim().toLowerCase(Locale.ROOT);
        Optional<EntityType> type = BukkitEntityResolver.spawnable("minecraft:" + typeId);
        if (type.isEmpty()) {
            reply(ctx, ItemworldMessageKey.SPAWNMOB_UNKNOWN, Map.of("mob", rawType));
            return Command.SINGLE_SUCCESS;
        }
        retype(ctx, player, type.get(), typeId);
        return Command.SINGLE_SUCCESS;
    }

    private void retype(CommandContext<CommandSourceStack> ctx, Player player, EntityType type, String typeId) {
        PlayerRef actor = ref(player);
        services.kernel().scheduler().onEntity(actor, () -> {
            Block target = player.getTargetBlockExact(REACH);
            if (target == null || !(target.getState() instanceof CreatureSpawner spawner)) {
                reply(ctx, ItemworldMessageKey.SPAWNER_NOT_A_SPAWNER);
                return;
            }
            spawner.setSpawnedType(type);
            spawner.update();
            reply(ctx, ItemworldMessageKey.SPAWNER_SET, Map.of("mob", typeId));
            services.audit().retypedSpawner(actor, typeId);
        });
    }
}

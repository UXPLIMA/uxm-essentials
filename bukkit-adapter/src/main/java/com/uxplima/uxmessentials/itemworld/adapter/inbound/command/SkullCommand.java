package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

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
import org.jspecify.annotations.NullMarked;

/**
 * {@code /skull [player]}: get a player-head skull — the named player's head, or the sender's own when no name
 * is given. The owning profile is resolved by name to an {@link OfflinePlayer}; the skull is added to the
 * player's inventory on their region thread and reported through {@link ItemworldMessageKey#SKULL_GIVEN}.
 */
@NullMarked
public final class SkullCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.skull.use";

    public SkullCommand(ItemworldServices services) {
        super(services, "skull", SubFeatureGroup.ITEM_UTILS, "Get a player-head skull.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> run(ctx, Optional.of(StringArgumentType.getString(ctx, "player")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> name) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        String owner = name.orElse(player.getName());
        give(ctx, player, owner);
        return Command.SINGLE_SUCCESS;
    }

    private void give(CommandContext<CommandSourceStack> ctx, Player player, String owner) {
        OfflinePlayer profile = player.getServer().getOfflinePlayer(owner);
        services.kernel().scheduler().onEntity(ref(player), () -> {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            if (skull.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(profile);
                skull.setItemMeta(meta);
            }
            player.getInventory().addItem(skull);
            reply(ctx, ItemworldMessageKey.SKULL_GIVEN, Map.of("player", owner));
        });
    }
}

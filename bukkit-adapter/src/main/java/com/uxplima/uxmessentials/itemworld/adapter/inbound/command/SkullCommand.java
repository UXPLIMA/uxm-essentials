package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.SkullData;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /skull [player]}: get a player-head skull — the named player's head, or the sender's own when no name
 * is given. The owning profile is resolved by name off the region thread; the skull is added to the player's
 * inventory on their region thread and reported through {@link ItemworldMessageKey#SKULL_GIVEN}.
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
                .then(CommandSuggestions.playerArgument("player")
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
        // Resolve the name -> profile off the region thread (the by-name lookup blocks); applying the head by
        // its resolved UUID at build time does not, so the region-thread hop below stays non-blocking.
        OfflinePlayer profile = player.getServer().getOfflinePlayer(owner);
        services.kernel().scheduler().onEntity(ref(player), () -> {
            ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD)
                    .skull(SkullData.ofUuid(profile.getUniqueId()))
                    .build();
            player.getInventory().addItem(head);
            reply(ctx, ItemworldMessageKey.SKULL_GIVEN, Map.of("player", owner));
        });
    }
}

package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.minimessage.MiniMessage;

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
 * {@code /itemname [name]}: rename the held item, or clear its name when called with no argument. The name is a
 * MiniMessage source string parsed into the display-name component; an empty hand replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}. Set and clear report through
 * {@link ItemworldMessageKey#ITEMNAME_SET} / {@link ItemworldMessageKey#ITEMNAME_CLEARED}.
 */
@NullMarked
public final class ItemNameCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.itemname.use";

    public ItemNameCommand(ItemworldServices services) {
        super(services, "itemname", SubFeatureGroup.ITEM_UTILS, "Rename the held item.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, Optional.of(StringArgumentType.getString(ctx, "name")))))
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
        Optional<ItemStack> held = heldItem(ctx, player);
        if (held.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        rename(ctx, player, held.get(), name);
        return Command.SINGLE_SUCCESS;
    }

    private void rename(CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand, Optional<String> name) {
        services.kernel().scheduler().onEntity(ref(player), () -> {
            ItemMeta meta = hand.getItemMeta();
            if (name.isPresent() && !name.get().isBlank()) {
                meta.displayName(MiniMessage.miniMessage().deserialize(name.get()));
                hand.setItemMeta(meta);
                reply(ctx, ItemworldMessageKey.ITEMNAME_SET, Map.of("name", name.get()));
            } else {
                meta.displayName(null);
                hand.setItemMeta(meta);
                reply(ctx, ItemworldMessageKey.ITEMNAME_CLEARED);
            }
        });
    }
}

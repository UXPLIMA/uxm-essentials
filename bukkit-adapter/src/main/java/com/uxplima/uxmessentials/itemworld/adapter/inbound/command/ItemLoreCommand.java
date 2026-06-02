package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /itemlore <set|add|clear> [text]}: edit the held item's lore. {@code set} replaces the lore with a
 * single line, {@code add} appends a line, {@code clear} removes all lore. Each line is a MiniMessage source
 * string parsed into a component. An empty hand replies {@link ItemworldMessageKey#NO_ITEM_IN_HAND}; the three
 * modes report through {@link ItemworldMessageKey#ITEMLORE_SET} / {@code ITEMLORE_ADDED} /
 * {@code ITEMLORE_CLEARED}.
 */
@NullMarked
public final class ItemLoreCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.itemlore.use";

    public ItemLoreCommand(ItemworldServices services) {
        super(services, "itemlore", SubFeatureGroup.ITEM_UTILS, "Edit held-item lore.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("clear").executes(ctx -> run(ctx, Mode.CLEAR, Optional.empty())))
                .then(loreMode("set", Mode.SET))
                .then(loreMode("add", Mode.ADD))
                .build();
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> loreMode(String name, Mode mode) {
        return Commands.literal(name)
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, mode, Optional.of(StringArgumentType.getString(ctx, "text")))));
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, Mode mode, Optional<String> text) {
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
        edit(ctx, player, held.get(), mode, text);
        return Command.SINGLE_SUCCESS;
    }

    private void edit(
            CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand, Mode mode, Optional<String> text) {
        services.kernel().scheduler().onEntity(ref(player), () -> {
            ItemStack built =
                    switch (mode) {
                        case SET -> applySet(ctx, hand, text);
                        case ADD -> applyAdd(ctx, hand, text);
                        case CLEAR -> applyClear(ctx, hand);
                    };
            player.getInventory().setItemInMainHand(built);
        });
    }

    private ItemStack applySet(CommandContext<CommandSourceStack> ctx, ItemStack hand, Optional<String> text) {
        String line = text.orElse("");
        ItemStack built = ItemBuilder.from(hand)
                .lore(MiniMessage.miniMessage().deserialize(line))
                .build();
        reply(ctx, ItemworldMessageKey.ITEMLORE_SET, Map.of("text", line));
        return built;
    }

    private ItemStack applyAdd(CommandContext<CommandSourceStack> ctx, ItemStack hand, Optional<String> text) {
        String line = text.orElse("");
        ItemStack built = ItemBuilder.from(hand)
                .addLore(MiniMessage.miniMessage().deserialize(line))
                .build();
        reply(ctx, ItemworldMessageKey.ITEMLORE_ADDED, Map.of("text", line));
        return built;
    }

    private ItemStack applyClear(CommandContext<CommandSourceStack> ctx, ItemStack hand) {
        ItemStack built = ItemBuilder.from(hand).clearLore().build();
        reply(ctx, ItemworldMessageKey.ITEMLORE_CLEARED);
        return built;
    }

    private enum Mode {
        SET,
        ADD,
        CLEAR
    }
}

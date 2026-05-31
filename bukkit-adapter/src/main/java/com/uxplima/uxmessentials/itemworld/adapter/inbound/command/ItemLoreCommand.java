package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;
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
            ItemMeta meta = hand.getItemMeta();
            switch (mode) {
                case SET -> applySet(ctx, meta, text);
                case ADD -> applyAdd(ctx, meta, text);
                case CLEAR -> applyClear(ctx, meta);
            }
            hand.setItemMeta(meta);
        });
    }

    private void applySet(CommandContext<CommandSourceStack> ctx, ItemMeta meta, Optional<String> text) {
        String line = text.orElse("");
        meta.lore(List.of(MiniMessage.miniMessage().deserialize(line)));
        reply(ctx, ItemworldMessageKey.ITEMLORE_SET, Map.of("text", line));
    }

    private void applyAdd(CommandContext<CommandSourceStack> ctx, ItemMeta meta, Optional<String> text) {
        String line = text.orElse("");
        List<Component> lore = meta.hasLore() ? new ArrayList<>(requireLore(meta)) : new ArrayList<>();
        lore.add(MiniMessage.miniMessage().deserialize(line));
        meta.lore(lore);
        reply(ctx, ItemworldMessageKey.ITEMLORE_ADDED, Map.of("text", line));
    }

    private void applyClear(CommandContext<CommandSourceStack> ctx, ItemMeta meta) {
        meta.lore(null);
        reply(ctx, ItemworldMessageKey.ITEMLORE_CLEARED);
    }

    private static List<Component> requireLore(ItemMeta meta) {
        List<Component> lore = meta.lore();
        return lore == null ? List.of() : lore;
    }

    private enum Mode {
        SET,
        ADD,
        CLEAR
    }
}

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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.LorePolicy;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /itemedit}: the held-item editor. Renames the item ({@code rename <name>} / {@code resetname}) and edits
 * its lore ({@code lore add|set|insert|remove|clear}) — MiniMessage source for both, with the default italic
 * stripped by {@link ItemBuilder} so names and lines render upright. The lore line rules and their 1-based
 * index-bounds live in the pure {@link LorePolicy}; this adapter only reads the current lore off the item into a
 * string list, hands one operation to the policy, and writes the result back onto the {@link ItemMeta}.
 *
 * <p>Unlike the {@link SubFeatureGroup} verbs, {@code /itemedit} is gated by its own {@code item-edit.enabled}
 * flag ({@link com.uxplima.uxmessentials.itemworld.application.ItemworldConfig#itemEditEnabled()}); when off it
 * answers {@link ItemworldMessageKey#COMMAND_DISABLED} and mutates nothing. It is permission-gated in the
 * executor (rather than the node {@code requires}) so a denied actor gets a localized
 * {@link SharedMessageKey#COMMAND_NO_PERMISSION} reply, and an empty hand replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}. Every mutation runs on the actor's entity region thread.
 */
@NullMarked
public final class ItemEditCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.itemworld.itemedit";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public ItemEditCommand(ItemworldServices services) {
        super(services, "itemedit", SubFeatureGroup.ITEM_UTILS, "Edit the held item's name and lore.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> rename(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("resetname").executes(this::resetName))
                .then(loreNode())
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private LiteralArgumentBuilder<CommandSourceStack> loreNode() {
        return Commands.literal("lore")
                .then(Commands.literal("add")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> loreAdd(ctx, StringArgumentType.getString(ctx, "text")))))
                .then(indexed("set", this::loreSet))
                .then(indexed("insert", this::loreInsert))
                .then(Commands.literal("remove")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> loreRemove(ctx, IntegerArgumentType.getInteger(ctx, "index")))))
                .then(Commands.literal("clear").executes(this::loreClear));
    }

    private LiteralArgumentBuilder<CommandSourceStack> indexed(String name, IndexedLoreOp op) {
        return Commands.literal(name)
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> op.run(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "index"),
                                        StringArgumentType.getString(ctx, "text")))));
    }

    private int rename(CommandContext<CommandSourceStack> ctx, String name) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        apply(
                ctx,
                held,
                ItemBuilder.from(held.hand()).name(MINI.deserialize(name)).build(),
                ItemworldMessageKey.ITEMEDIT_NAME_SET,
                Map.of("name", name));
        return Command.SINGLE_SUCCESS;
    }

    private int resetName(CommandContext<CommandSourceStack> ctx) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        apply(
                ctx,
                held,
                ItemBuilder.from(held.hand()).clearName().build(),
                ItemworldMessageKey.ITEMEDIT_NAME_RESET,
                Map.of());
        return Command.SINGLE_SUCCESS;
    }

    private int loreAdd(CommandContext<CommandSourceStack> ctx, String text) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        writeLore(
                ctx,
                held,
                LorePolicy.add(currentLore(held.hand()), text),
                ItemworldMessageKey.ITEMEDIT_LORE_ADDED,
                Map.of("text", text));
        return Command.SINGLE_SUCCESS;
    }

    private int loreClear(CommandContext<CommandSourceStack> ctx) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        writeLore(ctx, held, LorePolicy.clear(), ItemworldMessageKey.ITEMEDIT_LORE_CLEARED, Map.of());
        return Command.SINGLE_SUCCESS;
    }

    private int loreSet(CommandContext<CommandSourceStack> ctx, int index, String text) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        return bounded(
                ctx,
                held,
                LorePolicy.set(currentLore(held.hand()), index, text),
                index,
                ItemworldMessageKey.ITEMEDIT_LORE_SET,
                Map.of("index", str(index), "text", text));
    }

    private int loreInsert(CommandContext<CommandSourceStack> ctx, int index, String text) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        return bounded(
                ctx,
                held,
                LorePolicy.insert(currentLore(held.hand()), index, text),
                index,
                ItemworldMessageKey.ITEMEDIT_LORE_INSERTED,
                Map.of("index", str(index), "text", text));
    }

    private int loreRemove(CommandContext<CommandSourceStack> ctx, int index) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        return bounded(
                ctx,
                held,
                LorePolicy.remove(currentLore(held.hand()), index),
                index,
                ItemworldMessageKey.ITEMEDIT_LORE_REMOVED,
                Map.of("index", str(index)));
    }

    /** Apply a bounded lore op: an empty result is an out-of-range line number, otherwise write the new lore. */
    private int bounded(
            CommandContext<CommandSourceStack> ctx,
            Held held,
            Optional<List<String>> next,
            int index,
            MessageKey key,
            Map<String, String> placeholders) {
        if (next.isEmpty()) {
            reply(ctx, ItemworldMessageKey.ITEMEDIT_LORE_OUT_OF_RANGE, Map.of("index", str(index)));
            return Command.SINGLE_SUCCESS;
        }
        writeLore(ctx, held, next.get(), key, placeholders);
        return Command.SINGLE_SUCCESS;
    }

    /** Resolve the config/permission gate and the held item once; {@code null} once a reply has been sent. */
    private @Nullable Held resolve(CommandContext<CommandSourceStack> ctx) {
        if (!services.config().itemEditEnabled()) {
            reply(ctx, ItemworldMessageKey.COMMAND_DISABLED, Map.of("command", literal()));
            return null;
        }
        Player player = player(ctx);
        if (player == null) {
            return null;
        }
        if (!services.kernel().permissions().has(ref(player), PERMISSION)) {
            reply(ctx, SharedMessageKey.COMMAND_NO_PERMISSION);
            return null;
        }
        Optional<ItemStack> hand = heldItem(ctx, player);
        if (hand.isEmpty()) {
            return null;
        }
        return new Held(player, hand.get());
    }

    private void writeLore(
            CommandContext<CommandSourceStack> ctx,
            Held held,
            List<String> lines,
            MessageKey key,
            Map<String, String> placeholders) {
        ItemBuilder builder = ItemBuilder.from(held.hand());
        apply(
                ctx,
                held,
                (lines.isEmpty() ? builder.clearLore() : builder.lore(toComponents(lines))).build(),
                key,
                placeholders);
    }

    private void apply(
            CommandContext<CommandSourceStack> ctx,
            Held held,
            ItemStack updated,
            MessageKey key,
            Map<String, String> placeholders) {
        services.kernel().scheduler().onEntity(ref(held.player()), () -> {
            held.player().getInventory().setItemInMainHand(updated);
            reply(ctx, key, placeholders);
        });
    }

    private static List<String> currentLore(ItemStack hand) {
        ItemMeta meta = hand.getItemMeta();
        @Nullable List<Component> lore = meta == null ? null : meta.lore();
        if (lore == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(lore.size());
        for (Component line : lore) {
            out.add(MINI.serialize(line));
        }
        return out;
    }

    private static List<Component> toComponents(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(MINI.deserialize(line));
        }
        return out;
    }

    private static String str(int value) {
        return Integer.toString(value);
    }

    /** The resolved actor and their main-hand item, captured once so each op reads a single consistent target. */
    private record Held(Player player, ItemStack hand) {}

    @FunctionalInterface
    private interface IndexedLoreOp {
        int run(CommandContext<CommandSourceStack> ctx, int index, String text);
    }
}

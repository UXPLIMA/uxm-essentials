package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemEdits;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.ItemEditView;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitItemResolver;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.AttributeSpec;
import com.uxplima.uxmessentials.itemworld.domain.EnchantSpec;
import com.uxplima.uxmessentials.itemworld.domain.LorePolicy;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /itemedit}: the held-item editor. Renames the item ({@code rename <name>} / {@code resetname}), edits
 * its lore ({@code lore add|set|insert|remove|clear}), and carries the advanced meta editors: {@code enchant
 * <enchant> <level>} (level {@code 0} or {@code unenchant} removes; over-vanilla-max is gated by
 * {@code item-edit.allow-over-max-enchants}), {@code flag <ItemFlag> [on|off]}, {@code attribute add|remove},
 * {@code durability <value>} / {@code repair}, {@code unbreakable [on|off]}, and {@code custommodeldata <int>} /
 * {@code custommodeldata clear}. Names and lore lines are MiniMessage source with the default italic stripped by
 * {@link ItemBuilder} so they render upright.
 *
 * <p>The lore line rules live in the pure {@link LorePolicy}; the enchant level-clamp in {@link EnchantSpec} and
 * the attribute id/amount/slot validation in {@link AttributeSpec}. This adapter resolves the Bukkit
 * {@link Enchantment} / {@link Attribute} / {@link ItemFlag} / {@link EquipmentSlotGroup} at the boundary through
 * {@link BukkitItemResolver} and writes the result back onto the {@link ItemMeta} via {@link ItemBuilder}.
 *
 * <p>Unlike the {@link SubFeatureGroup} verbs, {@code /itemedit} is gated by its own {@code item-edit.enabled}
 * flag ({@link com.uxplima.uxmessentials.itemworld.application.ItemworldConfig#itemEditEnabled()}); when off it
 * answers {@link ItemworldMessageKey#COMMAND_DISABLED} and mutates nothing. It is permission-gated in the
 * executor (rather than the node {@code requires}) so a denied actor gets a localized
 * {@link SharedMessageKey#COMMAND_NO_PERMISSION} reply, and an empty hand replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}. Every mutation runs on the actor's entity region thread.
 *
 * <p>A bare {@code /itemedit} (no subcommand) opens the click-driven {@link ItemEditView} on the held item through
 * the same config/permission/held-item gate, so an operator can edit by clicking instead of typing; the GUI applies
 * every change through the shared {@link ItemEdits} the subcommands now also use, so the two paths never drift. Each
 * subcommand keeps working unchanged for a command-first workflow.
 */
@NullMarked
public final class ItemEditCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.itemworld.itemedit";

    /**
     * The click-driven editor a bare {@code /itemedit} opens; {@code null} on a wiring (or test) with no GUI, in which
     * case a bare {@code /itemedit} gates as usual and then does nothing rather than opening a window.
     */
    private final @Nullable ItemEditView view;

    public ItemEditCommand(ItemworldServices services, @Nullable ItemEditView view) {
        super(services, "itemedit", SubFeatureGroup.ITEM_UTILS, "Edit the held item's name, lore and meta.");
        this.view = view;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .executes(this::openEditor)
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> rename(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("resetname").executes(this::resetName))
                .then(loreNode())
                .then(enchantNode())
                .then(unenchantNode())
                .then(flagNode())
                .then(attributeNode())
                .then(durabilityNode())
                .then(Commands.literal("repair").executes(this::repair))
                .then(unbreakableNode())
                .then(modelNode())
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
                ItemEdits.rename(held.hand(), name),
                ItemworldMessageKey.ITEMEDIT_NAME_SET,
                Map.of("name", name));
        return Command.SINGLE_SUCCESS;
    }

    private int resetName(CommandContext<CommandSourceStack> ctx) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        apply(ctx, held, ItemEdits.resetName(held.hand()), ItemworldMessageKey.ITEMEDIT_NAME_RESET, Map.of());
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
                LorePolicy.add(ItemEdits.currentLore(held.hand()), text),
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
                LorePolicy.set(ItemEdits.currentLore(held.hand()), index, text),
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
                LorePolicy.insert(ItemEdits.currentLore(held.hand()), index, text),
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
                LorePolicy.remove(ItemEdits.currentLore(held.hand()), index),
                index,
                ItemworldMessageKey.ITEMEDIT_LORE_REMOVED,
                Map.of("index", str(index)));
    }

    // ---- Advanced meta editors: enchant / flag / attribute / durability / unbreakable / custom model data ----

    private LiteralArgumentBuilder<CommandSourceStack> enchantNode() {
        return Commands.literal("enchant")
                .then(enchantArgument()
                        .executes(ctx -> enchant(ctx, 1))
                        .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                .executes(ctx -> enchant(ctx, IntegerArgumentType.getInteger(ctx, "level")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> unenchantNode() {
        return Commands.literal("unenchant").then(enchantArgument().executes(ctx -> enchant(ctx, 0)));
    }

    /** Apply (or, at level 0, remove) an enchant on the held item, clamping the level through {@link EnchantSpec}. */
    private int enchant(CommandContext<CommandSourceStack> ctx, int level) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        String rawId = ctx.getArgument("enchant", String.class);
        Optional<Enchantment> enchant = BukkitItemResolver.enchantmentByToken(rawId);
        if (enchant.isEmpty()) {
            reply(ctx, ItemworldMessageKey.ITEMEDIT_ENCHANT_UNKNOWN, Map.of("enchant", rawId));
            return Command.SINGLE_SUCCESS;
        }
        if (level == 0) {
            return removeEnchant(ctx, held, enchant.get(), rawId);
        }
        applyEnchant(ctx, held, enchant.get(), rawId, level);
        return Command.SINGLE_SUCCESS;
    }

    private int removeEnchant(CommandContext<CommandSourceStack> ctx, Held held, Enchantment enchant, String rawId) {
        ItemMeta meta = held.hand().getItemMeta();
        if (meta == null || !meta.hasEnchant(enchant)) {
            reply(ctx, ItemworldMessageKey.ITEMEDIT_ENCHANT_NOT_PRESENT, Map.of("enchant", rawId));
            return Command.SINGLE_SUCCESS;
        }
        apply(
                ctx,
                held,
                ItemEdits.removeEnchant(held.hand(), enchant),
                ItemworldMessageKey.ITEMEDIT_ENCHANT_REMOVED,
                Map.of("enchant", rawId));
        return Command.SINGLE_SUCCESS;
    }

    private void applyEnchant(
            CommandContext<CommandSourceStack> ctx, Held held, Enchantment enchant, String rawId, int level) {
        EnchantSpec spec = EnchantSpec.forEdit(
                        rawId,
                        level,
                        enchant.getMaxLevel(),
                        services.config().itemEditAllowOverMaxEnchants(),
                        services.config().maxEnchantLevel())
                .orElseThrow();
        if (spec.clamped()) {
            reply(ctx, ItemworldMessageKey.ITEMEDIT_ENCHANT_CLAMPED, Map.of("level", str(spec.level())));
        }
        apply(
                ctx,
                held,
                ItemEdits.enchant(held.hand(), enchant, spec.level()),
                ItemworldMessageKey.ITEMEDIT_ENCHANTED,
                Map.of("enchant", rawId, "level", str(spec.level())));
    }

    private LiteralArgumentBuilder<CommandSourceStack> flagNode() {
        return Commands.literal("flag")
                .then(itemFlagArgument()
                        .executes(ctx -> flag(ctx, Optional.empty()))
                        .then(Commands.literal("on").executes(ctx -> flag(ctx, Optional.of(true))))
                        .then(Commands.literal("off").executes(ctx -> flag(ctx, Optional.of(false)))));
    }

    /** Toggle an {@link ItemFlag}; a bare {@code flag <name>} flips the current state, {@code on}/{@code off} set it. */
    private int flag(CommandContext<CommandSourceStack> ctx, Optional<Boolean> requested) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        String token = ctx.getArgument("flag", String.class);
        Optional<ItemFlag> flag = BukkitItemResolver.itemFlag(token);
        if (flag.isEmpty()) {
            reply(ctx, ItemworldMessageKey.ITEMEDIT_FLAG_UNKNOWN, Map.of("flag", token));
            return Command.SINGLE_SUCCESS;
        }
        ItemMeta meta = held.hand().getItemMeta();
        boolean state = requested.orElse(meta == null || !meta.hasItemFlag(flag.get()));
        apply(
                ctx,
                held,
                ItemEdits.setFlag(held.hand(), flag.get(), state),
                ItemworldMessageKey.ITEMEDIT_FLAG_TOGGLED,
                Map.of("flag", token, "state", state ? "on" : "off"));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> attributeNode() {
        return Commands.literal("attribute")
                .then(Commands.literal("add")
                        .then(attributeArgument()
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> attributeAdd(ctx, null))
                                        .then(slotGroupArgument()
                                                .executes(ctx -> attributeAdd(
                                                        ctx, StringArgumentType.getString(ctx, "slot")))))))
                .then(Commands.literal("remove").then(attributeArgument().executes(this::attributeRemove)));
    }

    /** Add an {@code add_number} attribute modifier, keyed uniquely under {@code uxmessentials:} per attribute. */
    private int attributeAdd(CommandContext<CommandSourceStack> ctx, @Nullable String rawSlot) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        String rawAttr = ctx.getArgument("attribute", String.class);
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        Optional<AttributeSpec> spec = AttributeSpec.of(rawAttr, amount, rawSlot);
        Optional<Attribute> attribute = spec.flatMap(BukkitItemResolver::attribute);
        if (spec.isEmpty() || attribute.isEmpty()) {
            reply(ctx, ItemworldMessageKey.ITEMEDIT_ATTRIBUTE_UNKNOWN, Map.of("attribute", rawAttr));
            return Command.SINGLE_SUCCESS;
        }
        Optional<EquipmentSlotGroup> slot =
                BukkitItemResolver.slotGroup(spec.get().slotGroup());
        if (slot.isEmpty()) {
            reply(
                    ctx,
                    ItemworldMessageKey.ITEMEDIT_ATTRIBUTE_BAD_SLOT,
                    Map.of("slot", spec.get().slotGroup()));
            return Command.SINGLE_SUCCESS;
        }
        applyAttribute(ctx, held, attribute.get(), rawAttr, amount, slot.get());
        return Command.SINGLE_SUCCESS;
    }

    private void applyAttribute(
            CommandContext<CommandSourceStack> ctx,
            Held held,
            Attribute attribute,
            String rawAttr,
            double amount,
            EquipmentSlotGroup slot) {
        NamespacedKey key =
                NamespacedKey.fromString("uxmessentials:" + attribute.getKey().value());
        if (key == null) {
            reply(ctx, ItemworldMessageKey.ITEMEDIT_ATTRIBUTE_UNKNOWN, Map.of("attribute", rawAttr));
            return;
        }
        AttributeModifier modifier = new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER, slot);
        apply(
                ctx,
                held,
                ItemBuilder.from(held.hand()).attribute(attribute, modifier).build(),
                ItemworldMessageKey.ITEMEDIT_ATTRIBUTE_ADDED,
                Map.of("attribute", rawAttr, "amount", str(amount)));
    }

    /** Remove every modifier the held item carries for the named attribute. */
    private int attributeRemove(CommandContext<CommandSourceStack> ctx) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        String rawAttr = ctx.getArgument("attribute", String.class);
        Optional<Attribute> attribute = AttributeSpec.of(rawAttr, 0.0, null).flatMap(BukkitItemResolver::attribute);
        if (attribute.isEmpty()) {
            reply(ctx, ItemworldMessageKey.ITEMEDIT_ATTRIBUTE_UNKNOWN, Map.of("attribute", rawAttr));
            return Command.SINGLE_SUCCESS;
        }
        apply(
                ctx,
                held,
                ItemBuilder.from(held.hand())
                        .editMeta(meta -> meta.removeAttributeModifier(attribute.get()))
                        .build(),
                ItemworldMessageKey.ITEMEDIT_ATTRIBUTE_REMOVED,
                Map.of("attribute", rawAttr));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> durabilityNode() {
        return Commands.literal("durability")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                        .executes(ctx -> durability(ctx, IntegerArgumentType.getInteger(ctx, "value"))));
    }

    /** Set the held item's durability damage, bounded by its max durability; non-damageable items are rejected. */
    private int durability(CommandContext<CommandSourceStack> ctx, int value) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        short max = held.hand().getType().getMaxDurability();
        if (max <= 0 || !(held.hand().getItemMeta() instanceof Damageable)) {
            reply(ctx, ItemworldMessageKey.ITEMEDIT_DURABILITY_NOT_DAMAGEABLE);
            return Command.SINGLE_SUCCESS;
        }
        if (value > max) {
            reply(ctx, ItemworldMessageKey.ITEMEDIT_DURABILITY_OUT_OF_RANGE, Map.of("max", str(max)));
            return Command.SINGLE_SUCCESS;
        }
        apply(
                ctx,
                held,
                ItemEdits.damage(held.hand(), value),
                ItemworldMessageKey.ITEMEDIT_DURABILITY_SET,
                Map.of("value", str(value)));
        return Command.SINGLE_SUCCESS;
    }

    /** Reset the held item's durability damage to zero; an undamaged item is a no-op with a note. */
    private int repair(CommandContext<CommandSourceStack> ctx) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (!(held.hand().getItemMeta() instanceof Damageable damageable) || !damageable.hasDamage()) {
            reply(ctx, ItemworldMessageKey.ITEMEDIT_REPAIR_NOTHING);
            return Command.SINGLE_SUCCESS;
        }
        apply(ctx, held, ItemEdits.damage(held.hand(), 0), ItemworldMessageKey.ITEMEDIT_REPAIRED, Map.of());
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> unbreakableNode() {
        return Commands.literal("unbreakable")
                .executes(ctx -> unbreakable(ctx, Optional.empty()))
                .then(Commands.literal("on").executes(ctx -> unbreakable(ctx, Optional.of(true))))
                .then(Commands.literal("off").executes(ctx -> unbreakable(ctx, Optional.of(false))));
    }

    /** Set the held item's unbreakable flag; a bare {@code unbreakable} flips the current state. */
    private int unbreakable(CommandContext<CommandSourceStack> ctx, Optional<Boolean> requested) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        ItemMeta meta = held.hand().getItemMeta();
        boolean state = requested.orElse(meta == null || !meta.isUnbreakable());
        apply(
                ctx,
                held,
                ItemEdits.unbreakable(held.hand(), state),
                ItemworldMessageKey.ITEMEDIT_UNBREAKABLE_SET,
                Map.of("state", state ? "on" : "off"));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> modelNode() {
        return Commands.literal("custommodeldata")
                .then(Commands.literal("clear").executes(ctx -> model(ctx, Optional.empty())))
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(ctx -> model(ctx, Optional.of(IntegerArgumentType.getInteger(ctx, "id")))));
    }

    /** Set or clear the held item's custom model data, written as the 1.21 float-list selector. */
    private int model(CommandContext<CommandSourceStack> ctx, Optional<Integer> id) {
        Held held = resolve(ctx);
        if (held == null) {
            return Command.SINGLE_SUCCESS;
        }
        ItemStack updated =
                ItemEdits.customModelData(held.hand(), id.isPresent() ? OptionalInt.of(id.get()) : OptionalInt.empty());
        if (id.isPresent()) {
            apply(ctx, held, updated, ItemworldMessageKey.ITEMEDIT_MODEL_SET, Map.of("id", str(id.get())));
        } else {
            apply(ctx, held, updated, ItemworldMessageKey.ITEMEDIT_MODEL_CLEARED, Map.of());
        }
        return Command.SINGLE_SUCCESS;
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
        apply(ctx, held, ItemEdits.withLore(held.hand(), lines), key, placeholders);
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

    /**
     * Open the click-driven editor for a bare {@code /itemedit}: the same config/permission/held-item gate the
     * subcommands run, then hand the held item to the GUI. A wiring without a view (a unit-test dispatcher) gates and
     * then does nothing, so the bare verb never opens an empty window.
     */
    private int openEditor(CommandContext<CommandSourceStack> ctx) {
        Held held = resolve(ctx);
        if (held == null || view == null) {
            return Command.SINGLE_SUCCESS;
        }
        view.open(held.player(), ref(held.player()));
        return Command.SINGLE_SUCCESS;
    }

    private static String str(int value) {
        return Integer.toString(value);
    }

    /** Render an attribute amount without a trailing {@code .0} when it is a whole number. */
    private static String str(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? Long.toString((long) value)
                : Double.toString(value);
    }

    /** The resolved actor and their main-hand item, captured once so each op reads a single consistent target. */
    private record Held(Player player, ItemStack hand) {}

    @FunctionalInterface
    private interface IndexedLoreOp {
        int run(CommandContext<CommandSourceStack> ctx, int index, String text);
    }
}

package com.uxplima.uxmessentials.itemworld.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The mechanical held-item transforms {@code /itemedit} applies, factored out of the command so the command surface
 * and the click-driven GUI editor apply a change through one code path rather than two. Each method takes the current
 * item and returns the edited copy; the shape rules that gate a change live in the pure domain
 * ({@link com.uxplima.uxmessentials.itemworld.domain.LorePolicy}, {@link
 * com.uxplima.uxmessentials.itemworld.domain.EnchantSpec}) and are called by both entry points before these apply the
 * result. This is glue over {@link ItemBuilder}, not a place for new edit rules: it holds no validation the command
 * did not already hold, so the GUI never drifts from the subcommand it mirrors.
 *
 * <p>Names and lore lines are MiniMessage source; {@link ItemBuilder} strips the default italic so they render upright,
 * exactly as the command has always written them.
 */
@NullMarked
public final class ItemEdits {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private ItemEdits() {}

    /** The item renamed to the given MiniMessage source, with the default italic stripped. */
    public static ItemStack rename(ItemStack item, String miniMessageName) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(miniMessageName, "miniMessageName");
        return ItemBuilder.from(item).name(MINI.deserialize(miniMessageName)).build();
    }

    /** The item with its display name cleared. */
    public static ItemStack resetName(ItemStack item) {
        Objects.requireNonNull(item, "item");
        return ItemBuilder.from(item).clearName().build();
    }

    /** The item's current lore as MiniMessage source lines, in order; empty when the item carries no lore. */
    public static List<String> currentLore(ItemStack item) {
        Objects.requireNonNull(item, "item");
        ItemMeta meta = item.getItemMeta();
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

    /** The item with its lore replaced by {@code lines} (each MiniMessage source); an empty list clears the lore. */
    public static ItemStack withLore(ItemStack item, List<String> lines) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(lines, "lines");
        ItemBuilder builder = ItemBuilder.from(item);
        if (lines.isEmpty()) {
            return builder.clearLore().build();
        }
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(MINI.deserialize(line));
        }
        return builder.lore(components).build();
    }

    /** The item with {@code enchant} applied at {@code level} (the caller has already clamped the level). */
    public static ItemStack enchant(ItemStack item, Enchantment enchant, int level) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(enchant, "enchant");
        return ItemBuilder.from(item).enchant(enchant, level).build();
    }

    /** The item with {@code enchant} removed. */
    public static ItemStack removeEnchant(ItemStack item, Enchantment enchant) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(enchant, "enchant");
        return ItemBuilder.from(item).removeEnchant(enchant).build();
    }

    /** The item with {@code flag} set on or off. */
    public static ItemStack setFlag(ItemStack item, ItemFlag flag, boolean on) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(flag, "flag");
        ItemBuilder builder = ItemBuilder.from(item);
        return (on ? builder.flags(flag) : builder.removeFlags(flag)).build();
    }

    /** The item with its unbreakable flag set to {@code on}. */
    public static ItemStack unbreakable(ItemStack item, boolean on) {
        Objects.requireNonNull(item, "item");
        return ItemBuilder.from(item).unbreakable(on).build();
    }

    /** The item with its custom model data set to {@code id}, written as the 1.21 float-list selector; empty clears it. */
    public static ItemStack customModelData(ItemStack item, OptionalInt id) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(id, "id");
        List<Float> floats = id.isPresent() ? List.of((float) id.getAsInt()) : List.of();
        return ItemBuilder.from(item).customModelDataFloats(floats).build();
    }

    /** The item with its durability damage set to {@code value} (the caller has already bounded it). */
    public static ItemStack damage(ItemStack item, int value) {
        Objects.requireNonNull(item, "item");
        return ItemBuilder.from(item).damage(value).build();
    }
}

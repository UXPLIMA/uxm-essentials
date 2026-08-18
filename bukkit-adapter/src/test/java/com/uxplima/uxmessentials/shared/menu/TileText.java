package com.uxplima.uxmessentials.shared.menu;

import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Reads what a rendered menu tile says, whichever half of the tooltip the text sits in.
 *
 * <p>The style canon keeps a tile's title on the first lore line under a blank display name, and a bare button
 * (a back arrow, a page arrow) in the display name with no lore at all. A golden test cares that a slot shows the
 * right title and the right facts under it, not which of the two places the client draws them from, so these two
 * readers hide that split: {@link #title} returns the title a player reads, and {@link #body} the lore beneath it.
 */
public final class TileText {

    /** The diamond the renderer opens a titled tile's first lore line with. */
    private static final String DIAMOND = "◆ ";

    private TileText() {}

    /** The title a viewer reads on {@code item}: its display name, or the title line at the top of its lore. */
    public static String title(ItemStack item) {
        Objects.requireNonNull(item, "item");
        Component name = Objects.requireNonNull(item.getItemMeta()).displayName();
        String plain = name == null ? "" : plain(name);
        if (!plain.isBlank()) {
            return plain;
        }
        List<Component> lore = item.lore();
        if (lore == null || lore.isEmpty()) {
            return "";
        }
        String head = plain(lore.get(0)).strip();
        return head.startsWith(DIAMOND) ? head.substring(DIAMOND.length()).strip() : "";
    }

    /** The lore under {@code item}'s title: its whole lore for a tile whose title is its display name. */
    public static List<Component> body(ItemStack item) {
        Objects.requireNonNull(item, "item");
        List<Component> lore = item.lore();
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        Component name = Objects.requireNonNull(item.getItemMeta()).displayName();
        boolean titledInLore = (name == null || plain(name).isBlank())
                && plain(lore.get(0)).strip().startsWith(DIAMOND);
        return titledInLore ? List.copyOf(lore.subList(1, lore.size())) : List.copyOf(lore);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}

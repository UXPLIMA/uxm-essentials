package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves one {@link MenuItemSpec} into the {@link ItemStack} a viewer sees. The spec carries only raw text —
 * a {@code @key}, an inline literal, or a {@code %placeholder%} — so this is where those forms turn into a
 * concrete material, an Adventure name and lore, and the cosmetic decor. Material placeholders expand to a
 * material name; name/lore placeholders expand inline before the text is rendered or looked up in the catalog.
 * Unknown materials fall back to {@link Material#STONE} rather than failing a render, so one bad spec line
 * never blanks a whole menu.
 */
@NullMarked
public final class ItemRenderer {

    /** A single {@code %token%} placeholder. {@code group(1)} is the bare token name. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\w+)%");

    private final GuiText guiText;
    private final PlaceholderRegistry placeholders;

    public ItemRenderer(GuiText guiText, PlaceholderRegistry placeholders) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
    }

    public ItemStack render(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        Material material = resolveMaterial(item.material(), ctx);
        Component name = resolveText(item.name(), ctx);
        List<Component> lore = new ArrayList<>(item.lore().size());
        for (String line : item.lore()) {
            lore.add(resolveText(line, ctx));
        }
        return applyDecor(ItemBuilder.of(material).name(name).lore(lore), item.decor())
                .build();
    }

    /**
     * The material to render. A {@code %token%} spec resolves through the placeholder whose value is treated as
     * a material name; a literal spec is the name itself. An unmatched name (or a blank one) falls back to
     * {@link Material#STONE} so a typo never aborts the render.
     */
    private Material resolveMaterial(String raw, MenuContext ctx) {
        Matcher matcher = PLACEHOLDER.matcher(raw);
        String name = matcher.find()
                ? placeholders.get(matcher.group(1)).map(fn -> fn.apply(ctx)).orElse("")
                : raw;
        if (name.isBlank()) {
            return Material.STONE;
        }
        Material material = Material.matchMaterial(name);
        return material != null ? material : Material.STONE;
    }

    /**
     * Resolve one text line. Every {@code %token%} is substituted with its placeholder value first; then a line
     * that originally began with {@code @} is looked up in the locale catalog (the rest is the message key),
     * while any other line is rendered as an inline MiniMessage literal. An empty spec yields an empty component
     * so the item simply has no name/lore line rather than a stray blank.
     */
    private Component resolveText(String s, MenuContext ctx) {
        if (s.isEmpty()) {
            return Component.empty();
        }
        String substituted = substitutePlaceholders(s, ctx);
        if (s.startsWith("@")) {
            String key = substituted.substring(1);
            // The catalog entry may carry {token} arguments (e.g. {sound}, {warp}); fill them from the same
            // placeholders a %token% would use, so a per-entry list item shows that entry's value.
            return guiText.text(ctx.viewer(), () -> key, placeholders.resolveAll(ctx));
        }
        return StyledText.render(substituted);
    }

    /** Replace every {@code %token%} in {@code source} with its registered placeholder value (or empty). */
    private String substitutePlaceholders(String source, MenuContext ctx) {
        Matcher matcher = PLACEHOLDER.matcher(source);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value =
                    placeholders.get(matcher.group(1)).map(fn -> fn.apply(ctx)).orElse("");
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Layer the spec's amount, glow, optional model data, and item flags onto the in-progress item. */
    private ItemBuilder applyDecor(ItemBuilder builder, ItemDecor decor) {
        builder.amount(decor.amount()).glow(decor.glow());
        decor.modelData().ifPresent(builder::customModelData);
        ItemFlag[] flags = resolveFlags(decor.flagTokens());
        if (flags.length > 0) {
            builder.flags(flags);
        }
        return builder;
    }

    /** Map the spec's raw flag tokens to Bukkit {@link ItemFlag}s, skipping any token that is not a flag name. */
    private ItemFlag[] resolveFlags(List<String> tokens) {
        List<ItemFlag> flags = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            try {
                flags.add(ItemFlag.valueOf(token));
            } catch (IllegalArgumentException unknownFlag) {
                // A spec naming a flag that does not exist on this server should not abort the render.
            }
        }
        return flags.toArray(ItemFlag[]::new);
    }
}

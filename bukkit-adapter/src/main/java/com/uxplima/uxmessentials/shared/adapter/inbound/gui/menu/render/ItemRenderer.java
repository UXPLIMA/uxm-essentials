package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviders;
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
    private final IconProviders iconProviders;

    /**
     * The plain two-argument form, kept so the engine's many existing call sites are untouched. It renders with
     * the {@link IconProviders#defaults() default} icon chain — skull sources and viewer equipment — so a menu
     * gains those for free; HeadDatabase needs the hook and so is only present on the three-argument form.
     */
    public ItemRenderer(GuiText guiText, PlaceholderRegistry placeholders) {
        this(guiText, placeholders, IconProviders.defaults());
    }

    /**
     * The full form the composition root uses: the same renderer plus an explicit {@link IconProviders} chain,
     * which bootstrap builds with HeadDatabase wired in so {@code hdb:<id>} resolves (and degrades to a plain
     * head when HeadDatabase is absent).
     */
    public ItemRenderer(GuiText guiText, PlaceholderRegistry placeholders, IconProviders iconProviders) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
        this.iconProviders = Objects.requireNonNull(iconProviders, "iconProviders");
    }

    /**
     * Resolve a menu's raw title string for {@code ctx} the same way an item name resolves: every {@code %token%}
     * is substituted, then a {@code @key} title is looked up in the viewer's catalog with its {@code {token}}
     * arguments filled from the same placeholders. This lets a subject-driven title (a home's label, a warp's name)
     * render through the open context rather than being frozen to a static key. A blank title yields an empty
     * component so a spec may still open titleless.
     */
    public Component title(String rawTitle, MenuContext ctx) {
        Objects.requireNonNull(rawTitle, "rawTitle");
        Objects.requireNonNull(ctx, "ctx");
        return resolveText(rawTitle, ctx);
    }

    public ItemStack render(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        String materialSpec = resolveMaterialSpec(item.material(), ctx);
        Component name = resolveText(item.name(), ctx);
        return applyDecor(baseItem(materialSpec, ctx).name(name).lore(lore(item, ctx)), item.decor())
                .build();
    }

    /**
     * Build the lore components for {@code item}. Each spec line maps to one component as before, except an
     * inline/placeholder literal whose resolved value carries newlines, which expands into one component per
     * {@code \n}-separated segment. This lets a per-entry icon (the kit/warp browse rows) emit a variable number
     * of lore lines from a single {@code %placeholder%} — a {@code ✔/✘} per requirement, plus the conditional
     * cooldown/cost/claimable lines — rather than being capped at the spec's fixed line count.
     */
    public List<Component> lore(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        List<Component> out = new ArrayList<>(item.lore().size());
        for (String line : item.lore()) {
            appendLore(line, ctx, out);
        }
        return out;
    }

    /**
     * Append the component(s) for one lore spec line to {@code out}. A blank spec stays one blank line, and a
     * {@code @key} catalog line stays a single component (the catalog owns its own layout, so its output is never
     * split here). Any other line is an inline/placeholder literal: its {@code %token%}s are substituted, then the
     * result is split on {@code \n} so a multi-line placeholder value becomes one lore component per segment. The
     * {@code -1} split limit keeps trailing empty segments, and a value with no newline yields exactly one
     * component — identical to a plain literal line.
     */
    private void appendLore(String spec, MenuContext ctx, List<Component> out) {
        if (spec.isEmpty()) {
            out.add(Component.empty());
            return;
        }
        if (spec.startsWith("@")) {
            out.add(resolveText(spec, ctx));
            return;
        }
        String substituted = substitutePlaceholders(spec, ctx);
        for (String segment : substituted.split("\n", -1)) {
            out.add(StyledText.render(segment));
        }
    }

    /**
     * The resolved material spec string: a {@code %token%} spec expands through its placeholder, a literal spec
     * is itself. This is the string the icon providers and the material fallback both read — so {@code %head%}
     * → {@code skull:Notch} reaches the skull provider, and {@code DIAMOND} reaches the material lookup.
     */
    private String resolveMaterialSpec(String raw, MenuContext ctx) {
        Matcher matcher = PLACEHOLDER.matcher(raw);
        return matcher.find() ? placeholders.resolve(matcher.group(1), ctx).orElse("") : raw;
    }

    /**
     * The base item for {@code spec}: an icon provider's stack (a skull source, the viewer's equipment, an HDB
     * head) when one claims the spec, else a plain item of the named material. A provider that builds a head
     * still has the item's name, lore and decor layered on top by the caller, exactly as a material item does.
     */
    private ItemBuilder baseItem(String spec, MenuContext ctx) {
        Optional<ItemStack> provided = iconProviders.resolve(spec, ctx);
        return provided.map(ItemBuilder::from).orElseGet(() -> ItemBuilder.of(materialOrStone(spec)));
    }

    /**
     * The material named by {@code name}, falling back to {@link Material#STONE} for a blank or unknown name so a
     * typo (or a provider-shaped spec no provider claimed) never aborts the render.
     */
    private Material materialOrStone(String name) {
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
            String value = placeholders.resolve(matcher.group(1), ctx).orElse("");
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

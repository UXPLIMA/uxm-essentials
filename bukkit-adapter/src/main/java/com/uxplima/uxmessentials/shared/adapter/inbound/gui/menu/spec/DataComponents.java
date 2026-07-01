package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The native data-components an operator layers onto a menu item beyond {@link RichMeta}'s enchants/colours/trim —
 * a display rarity, a tooltip-style key, the hide-tooltip and enchant-glint toggles, an enchantability rating,
 * attribute modifiers, and the food/tool components. Every value is a plain string, int, double, boolean, or list
 * — never a Bukkit type — so this model stays in the pure, Bukkit-free spec core; the renderer resolves each token
 * against the live registries at draw time and silently skips anything that does not resolve.
 *
 * <p>The three toggles are {@link Optional}{@code <Boolean>} rather than a bare {@code boolean} on purpose: an unset
 * toggle leaves the item's natural value alone, so an operator who never writes {@code hide-tooltip} does not force
 * it false. Each attribute-modifier token is {@code attribute:amount:operation:slot} (the operation and slot are
 * optional, defaulting to {@code add_number} and {@code any}); the renderer derives a unique key per modifier so
 * repeated entries on one item never collide.
 */
public record DataComponents(
        Optional<String> rarity,
        Optional<String> tooltipStyle,
        Optional<Boolean> hideTooltip,
        Optional<Boolean> enchantGlint,
        Optional<Integer> enchantable,
        List<String> attributeModifiers,
        Optional<FoodSpec> food,
        Optional<ToolSpec> tool) {

    /** The empty data-components — the default an item carries when its {@code decor} block declares none. */
    public static final DataComponents NONE = new DataComponents(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            List.of(),
            Optional.empty(),
            Optional.empty());

    public DataComponents {
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(tooltipStyle, "tooltipStyle");
        Objects.requireNonNull(hideTooltip, "hideTooltip");
        Objects.requireNonNull(enchantGlint, "enchantGlint");
        Objects.requireNonNull(enchantable, "enchantable");
        attributeModifiers = List.copyOf(Objects.requireNonNull(attributeModifiers, "attributeModifiers"));
        Objects.requireNonNull(food, "food");
        Objects.requireNonNull(tool, "tool");
    }

    /**
     * The food component: how much hunger and saturation eating restores, and whether it can be eaten on a full
     * bar. Each field is optional so a spec may set only the ones it cares about, leaving the rest at the value the
     * fresh component carries.
     */
    public record FoodSpec(Optional<Integer> nutrition, Optional<Double> saturation, Optional<Boolean> canAlwaysEat) {

        /** The empty food spec — no overrides. */
        public static final FoodSpec NONE = new FoodSpec(Optional.empty(), Optional.empty(), Optional.empty());

        public FoodSpec {
            Objects.requireNonNull(nutrition, "nutrition");
            Objects.requireNonNull(saturation, "saturation");
            Objects.requireNonNull(canAlwaysEat, "canAlwaysEat");
        }
    }

    /** The tool component: the base mining speed against blocks the tool is not suited to, and durability per block. */
    public record ToolSpec(Optional<Double> defaultMiningSpeed, Optional<Integer> damagePerBlock) {

        /** The empty tool spec — no overrides. */
        public static final ToolSpec NONE = new ToolSpec(Optional.empty(), Optional.empty());

        public ToolSpec {
            Objects.requireNonNull(defaultMiningSpeed, "defaultMiningSpeed");
            Objects.requireNonNull(damagePerBlock, "damagePerBlock");
        }
    }
}

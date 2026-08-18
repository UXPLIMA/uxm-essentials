package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The title treatment the style canon gives a menu tile: the text in bold, drawn in a gradient that runs from a
 * palette colour to its lighter neighbour.
 *
 * <p>A gradient rather than a flat colour is what tells a title apart from the lines under it without adding a
 * second colour to the tooltip, and running it inside one palette family is what stops it reading as two colours
 * fighting. Which family it runs in is decided by the colour the catalog already gave the title, so meaning
 * survives the treatment: a refusal or a delete stays red, money stays green, a call to act stays gold, and
 * everything else reads in the brand sky. That mapping is the reason this takes a {@link Component} and not a
 * string; the incoming colour is the input, and it is then replaced.
 *
 * <p>Only the visible characters take a step along the ramp. A space keeps its place in the text but not in the
 * count, so two titles of the same length land on the same colours whether or not they are one word.
 */
@NullMarked
public final class Gradients {

    /** One ramp: a palette colour and the lighter one beside it in the same family. */
    private record Ramp(TextColor from, TextColor to) {}

    private static final Ramp SKY = new Ramp(StyleTags.ACCENT, StyleTags.VALUE);
    private static final Ramp MONEY = new Ramp(StyleTags.EMERALD, StyleTags.GOOD);
    private static final Ramp DANGER = new Ramp(StyleTags.BAD, StyleTags.ROSE);
    private static final Ramp ATTENTION = new Ramp(StyleTags.GOLD, StyleTags.YELLOW);

    private Gradients() {}

    /**
     * {@code name} as a tile title: its own text, bold, in the gradient its colour maps to. The result carries no
     * trace of the incoming style, so two tiles titled the same way look the same however their catalog keys were
     * written.
     */
    public static Component title(Component name) {
        Objects.requireNonNull(name, "name");
        String plain = PlainTextComponentSerializer.plainText().serialize(name);
        if (plain.isBlank()) {
            return name;
        }
        return ramped(plain, rampOf(name)).decoration(TextDecoration.BOLD, true);
    }

    /** {@code plain} with each visible character a step further along {@code ramp}. */
    private static Component ramped(String plain, Ramp ramp) {
        int steps = Math.max(1, (int) plain.chars().filter(ch -> ch != ' ').count() - 1);
        TextComponent.Builder out = Component.text();
        int visible = 0;
        for (int i = 0; i < plain.length(); i++) {
            String character = plain.substring(i, i + 1);
            if (character.equals(" ")) {
                out.append(Component.text(character));
                continue;
            }
            float step = (float) visible / steps;
            out.append(Component.text(character, TextColor.lerp(step, ramp.from(), ramp.to())));
            visible++;
        }
        return out.build();
    }

    /**
     * The family {@code name} belongs to, read off the first colour anywhere in it. A title the catalog left
     * uncoloured, or coloured with one of the reading greys, is neutral and reads in the brand sky.
     */
    private static Ramp rampOf(Component name) {
        @Nullable TextColor colour = firstColour(name);
        if (StyleTags.BAD.equals(colour)) {
            return DANGER;
        }
        if (StyleTags.GOOD.equals(colour) || StyleTags.EMERALD.equals(colour)) {
            return MONEY;
        }
        if (StyleTags.GOLD.equals(colour)) {
            return ATTENTION;
        }
        return SKY;
    }

    /** The first colour set anywhere in {@code component}, depth first, or {@code null} when it carries none. */
    @Nullable private static TextColor firstColour(Component component) {
        @Nullable TextColor own = component.style().color();
        if (own != null) {
            return own;
        }
        for (Component child : component.children()) {
            @Nullable TextColor colour = firstColour(child);
            if (colour != null) {
                return colour;
            }
        }
        return null;
    }
}

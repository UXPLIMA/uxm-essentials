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

    // The four ramps are read from the palette each time rather than held, because the palette is read from a
    // file and a server may reload it while the server runs. A ramp cached here would keep the colours the
    // class happened to load with.
    private static Ramp sky() {
        return new Ramp(StyleTags.accent(), StyleTags.value());
    }

    private static Ramp money() {
        return new Ramp(StyleTags.emerald(), StyleTags.good());
    }

    private static Ramp danger() {
        return new Ramp(StyleTags.bad(), StyleTags.rose());
    }

    private static Ramp attention() {
        return new Ramp(StyleTags.gold(), StyleTags.yellow());
    }

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

    /**
     * {@code text} painted across {@code from} to {@code to}, in bold, whatever colour it arrived with.
     *
     * <p>The seam a heading uses when the file that wrote it named a wheel position rather than a meaning.
     * The two colours come from the theme, so this decides nothing about the look on its own.
     */
    public static Component across(Component text, TextColor from, TextColor to) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        String plain = PlainTextComponentSerializer.plainText().serialize(text);
        if (plain.isBlank()) {
            return text;
        }
        return ramped(plain, new Ramp(from, to)).decoration(TextDecoration.BOLD, true);
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
        if (StyleTags.bad().equals(colour)) {
            return danger();
        }
        if (StyleTags.good().equals(colour) || StyleTags.emerald().equals(colour)) {
            return money();
        }
        if (StyleTags.gold().equals(colour)) {
            return attention();
        }
        return sky();
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

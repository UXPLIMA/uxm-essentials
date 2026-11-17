package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.jspecify.annotations.NullMarked;

/**
 * How a menu placeholder's value goes into a line: as text, escaped where it is bound, or as markup, as it is.
 *
 * <p>The menu engine puts a bound value into two kinds of line, and it is the same value in both. A line written in the
 * menu file gets the value substituted straight into its MiniMessage. A {@code @key} line hands it to {@link GuiText},
 * which takes it as it is. So a value is made fit for a line here, once: text is escaped, and a tag in it reads as
 * written in either kind of line. A value bound as markup is a catalogue line the plugin resolved, an operator's own
 * styled name, or text escaped where it was put together.
 *
 * <p>The escape is in the value rather than a mark beside it because the engine also reads a value as a material, an
 * amount, a model number or an operand of a condition. Escaping touches only {@code <} and {@code \}, which none of
 * those hold, so a value bound as text works there unchanged.
 */
@NullMarked
public final class MenuText {

    private MenuText() {}

    /** Bind {@code name} to text: the value is escaped, so a tag in it reads as written in every line. */
    public static void text(MenuBindings bindings, String name, Function<MenuContext, String> value) {
        Objects.requireNonNull(value, "value");
        bindings.placeholder(name, ctx -> StyledText.escape(value.apply(ctx)));
    }

    /**
     * Bind {@code name} to markup, which goes into a line as it is: a catalogue line the plugin resolved, an operator's
     * own styled name, or text already escaped where it was put together. Never a value a player typed as it stands.
     */
    public static void markup(MenuBindings bindings, String name, Function<MenuContext, String> value) {
        bindings.placeholder(name, Objects.requireNonNull(value, "value"));
    }

    /** Answer every name that starts with {@code prefix} with text, escaped like {@link #text}. */
    public static void textFallback(
            MenuBindings bindings, String prefix, BiFunction<String, MenuContext, String> value) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(value, "value");
        bindings.placeholders()
                .fallback(id -> id.startsWith(prefix), (id, ctx) -> StyledText.escape(value.apply(id, ctx)));
    }

    /** Answer every name that starts with {@code prefix} with markup, like {@link #markup}. */
    public static void markupFallback(
            MenuBindings bindings, String prefix, BiFunction<String, MenuContext, String> value) {
        Objects.requireNonNull(prefix, "prefix");
        bindings.placeholders().fallback(id -> id.startsWith(prefix), Objects.requireNonNull(value, "value"));
    }
}

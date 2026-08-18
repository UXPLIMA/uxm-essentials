package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

/**
 * Pins how a window titles itself: centred, and bare. The canon writes a menu title with no colour, no bold and
 * no dashes around it, and the stripping happens here rather than in the catalog so a key that still carries a
 * colour tag cannot paint a two-tone title.
 */
class MenuTitlesTest {

    @Test
    void aTitleIsPaddedIntoTheMiddleOfTheWindow() {
        Component centred = MenuTitles.centre(Component.text("Warps"));

        String plain = PlainTextComponentSerializer.plainText().serialize(centred);
        assertThat(plain).startsWith(" ").endsWith("Warps");
        assertThat(plain.strip()).isEqualTo("Warps");
    }

    @Test
    void noDashesAreWrappedAroundIt() {
        String plain = PlainTextComponentSerializer.plainText().serialize(MenuTitles.centre(Component.text("Warps")));

        assertThat(plain).doesNotContain("-");
    }

    @Test
    void everyColourAndDecorationIsDropped() {
        Component painted = Component.text("Home", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(" Castle", NamedTextColor.AQUA));

        Component centred = MenuTitles.centre(painted);

        assertThat(centred.color()).isNull();
        assertThat(centred.decoration(TextDecoration.BOLD)).isEqualTo(TextDecoration.State.NOT_SET);
        assertThat(centred.children()).isEmpty();
        assertThat(PlainTextComponentSerializer.plainText().serialize(centred).strip())
                .isEqualTo("Home Castle");
    }

    @Test
    void aBlankTitleIsHandedBackUntouchedRatherThanPaddedIntoSpaces() {
        Component blank = Component.empty();

        assertThat(MenuTitles.centre(blank)).isSameAs(blank);
    }
}

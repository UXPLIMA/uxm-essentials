package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.GlyphWidthTable;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Pins how a window titles itself: centred unless the theme says left, padded only when its width can be measured,
 * and drawn with whatever style it was written in. The house look, bare titles, is kept in the shipped catalogues.
 */
class MenuTitlesTest {

    @Test
    void aTitleIsPaddedIntoTheMiddleOfTheWindow() {
        Component centred = StyledText.title(Component.text("Warps"));

        String plain = PlainTextComponentSerializer.plainText().serialize(centred);
        assertThat(plain).startsWith(" ").endsWith("Warps");
        assertThat(plain.strip()).isEqualTo("Warps");
    }

    @Test
    void thePaddingPutsTheTitleWithinTwoPixelsOfTheMiddle() {
        // The client draws a chest label from an origin eight pixels inside a window 176 wide, so a title is
        // centred when half the free width either side of it, less that origin, is spent on spaces.
        for (String title : new String[] {"Warps", "Management hub", "A", "The longest title a window holds"}) {
            String plain = PlainTextComponentSerializer.plainText().serialize(StyledText.title(Component.text(title)));
            int pad = plain.length() - plain.stripLeading().length();
            int centre = 8 + 4 * pad + GlyphWidthTable.widthOf(title, false) / 2;

            assertThat(Math.abs(centre - 88))
                    .as("title '%s' sits at %d", title, centre)
                    .isLessThanOrEqualTo(2);
        }
    }

    @Test
    void noDashesAreWrappedAroundIt() {
        String plain = PlainTextComponentSerializer.plainText().serialize(StyledText.title(Component.text("Warps")));

        assertThat(plain).doesNotContain("-");
    }

    /**
     * What the operator wrote is what the window shows. Titles were once flattened to plain text here, which kept the
     * house look and broke every title an operator styled: a translated key came out as its raw name and a font from
     * a resource pack was dropped. The shipped titles are bare where they are written now, which {@code
     * ShippedWindowTitlesAreBareTest} keeps.
     */
    @Test
    void aStyleTheOperatorWroteIsKept() {
        Component painted = Component.text("Home", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(" Castle", NamedTextColor.AQUA));

        Component centred = StyledText.title(painted);

        assertThat(centred.children()).contains(painted);
        assertThat(PlainTextComponentSerializer.plainText().serialize(centred))
                .startsWith(" ")
                .endsWith("Home Castle");
    }

    /** A title drawn with a resource pack is lined up by the pack, so it is never padded from letters it does not use. */
    @Test
    void aTitleDrawnWithAPackIsNotPadded() {
        Component negativeSpace = Component.translatable("space.-8").append(Component.text("Main Menu"));
        Component glyph = Component.text(
                "\uE000", Style.style().font(Key.key("pack", "menus")).build());

        assertThat(StyledText.title(negativeSpace)).isSameAs(negativeSpace);
        assertThat(StyledText.title(glyph)).isSameAs(glyph);
    }

    /** A theme that says left leaves every title where the client draws it. */
    @Test
    void aLeftThemeLeavesTheTitleAsWritten() throws SerializationException {
        Component title = Component.text("Warps");
        try {
            StyleTags.use(Theme.from(leftTitles()));
            assertThat(StyledText.title(title)).isSameAs(title);
        } finally {
            StyleTags.use(ThemeFile.shippedTheme());
        }
        assertThat(PlainTextComponentSerializer.plainText().serialize(StyledText.title(title)))
                .startsWith(" ");
    }

    @Test
    void aBlankTitleIsHandedBackUntouchedRatherThanPaddedIntoSpaces() {
        Component blank = Component.empty();

        assertThat(StyledText.title(blank)).isSameAs(blank);
    }

    private static ConfigurationNode leftTitles() throws SerializationException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("menu-titles").set("left");
        return node;
    }
}

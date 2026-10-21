package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

/** The ramp a tile title is painted in, read off the title line the way a client draws it. */
class TileTitleRampsTest {

    private static final Component NAME = Component.text("Warps");
    private static final List<Component> LORE = List.of(Component.text("go somewhere"));

    @Test
    void aTitleRunsFromSkyToIceAndIsBold() {
        Component title = title(NAME);

        assertThat(title.decoration(TextDecoration.BOLD)).isEqualTo(TextDecoration.State.TRUE);
        List<TextColor> colours = letterColours(title);
        assertThat(colours.get(0)).isEqualTo(StyleTags.accent());
        assertThat(colours.get(colours.size() - 1)).isEqualTo(StyleTags.value());
    }

    @Test
    void theTextItselfIsUntouched() {
        assertThat(PlainTextComponentSerializer.plainText().serialize(title(NAME)))
                .isEqualTo("Warps");
    }

    @Test
    void aDangerTitleStaysRedAndAMoneyTitleStaysGreen() {
        assertThat(letterColours(title(NAME.color(StyleTags.bad()))).get(0)).isEqualTo(StyleTags.bad());
        assertThat(letterColours(title(NAME.color(StyleTags.good()))).get(0)).isEqualTo(StyleTags.emerald());
        assertThat(letterColours(title(NAME.color(StyleTags.gold()))).get(0)).isEqualTo(StyleTags.gold());
    }

    @Test
    void aColourTheCatalogUsesForReadingIsNeutral() {
        // Ice, white and the greys say nothing about meaning, so a title wearing one opens on the brand sky.
        assertThat(letterColours(title(NAME.color(StyleTags.value()))).get(0)).isEqualTo(StyleTags.accent());
        assertThat(letterColours(title(NAME.color(StyleTags.body()))).get(0)).isEqualTo(StyleTags.accent());
    }

    @Test
    void aSpaceKeepsItsPlaceButNotItsStepAlongTheRamp() {
        // Two words end on the same colour a single word of the same visible length would.
        List<TextColor> spaced = letterColours(title(Component.text("Ne xt")));
        List<TextColor> solid = letterColours(title(Component.text("Next")));

        assertThat(spaced.get(spaced.size() - 1)).isEqualTo(solid.get(solid.size() - 1));
    }

    @Test
    void aBlankTitleIsHandedBackUntouched() {
        Component blank = Component.text(" ");

        assertThat(Tiles.titled(blank, LORE)).isSameAs(LORE);
    }

    /** The painted title inside a tile's title line: the line is a pad, the glyph, the title, then a pad. */
    private static Component title(Component name) {
        return Tiles.titled(name, LORE).get(0).children().get(1);
    }

    /** The colour each visible letter of {@code component} is drawn in, spaces left out. */
    private static List<TextColor> letterColours(Component component) {
        List<TextColor> colours = new ArrayList<>();
        collect(component, Style.empty(), colours);
        return colours;
    }

    private static void collect(Component component, Style inherited, List<TextColor> colours) {
        Style style = inherited.merge(component.style(), Style.Merge.Strategy.ALWAYS);
        if (component instanceof TextComponent text) {
            for (int i = 0; i < text.content().length(); i++) {
                if (text.content().charAt(i) != ' ') {
                    colours.add(style.color());
                }
            }
        }
        for (Component child : component.children()) {
            collect(child, style, colours);
        }
    }
}

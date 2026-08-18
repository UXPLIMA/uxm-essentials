package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

/**
 * Pins the title treatment: bold, and a gradient that starts on a palette colour and ends on the lighter one
 * beside it. Which family it runs in follows the colour the catalog gave the title, so a delete still reads red
 * and a payout still reads green, while everything neutral reads in the brand sky whatever it was painted before.
 */
class GradientsTest {

    private static final Component NAME = Component.text("Warps");

    @Test
    void aTitleRunsFromSkyToIceAndIsBold() {
        Component title = Gradients.title(NAME);

        assertThat(title.decoration(TextDecoration.BOLD)).isEqualTo(TextDecoration.State.TRUE);
        assertThat(title.children().get(0).color()).isEqualTo(StyleTags.ACCENT);
        assertThat(title.children().get(4).color()).isEqualTo(StyleTags.VALUE);
    }

    @Test
    void theTextItselfIsUntouched() {
        assertThat(PlainTextComponentSerializer.plainText().serialize(Gradients.title(NAME)))
                .isEqualTo("Warps");
    }

    @Test
    void aDangerTitleStaysRedAndAMoneyTitleStaysGreen() {
        assertThat(Gradients.title(NAME.color(StyleTags.BAD)).children().get(0).color())
                .isEqualTo(StyleTags.BAD);
        assertThat(Gradients.title(NAME.color(StyleTags.GOOD)).children().get(0).color())
                .isEqualTo(StyleTags.EMERALD);
        assertThat(Gradients.title(NAME.color(StyleTags.GOLD)).children().get(0).color())
                .isEqualTo(StyleTags.GOLD);
    }

    @Test
    void aColourTheCatalogUsesForReadingIsNeutral() {
        // Ice, white and the greys say nothing about meaning, so a title wearing one opens on the brand sky.
        assertThat(Gradients.title(NAME.color(StyleTags.VALUE))
                        .children()
                        .get(0)
                        .color())
                .isEqualTo(StyleTags.ACCENT);
        assertThat(Gradients.title(NAME.color(StyleTags.BODY)).children().get(0).color())
                .isEqualTo(StyleTags.ACCENT);
    }

    @Test
    void aSpaceKeepsItsPlaceButNotItsStepAlongTheRamp() {
        // Two words end on the same colour a single word of the same visible length would.
        Component spaced = Gradients.title(Component.text("Ne xt"));
        Component solid = Gradients.title(Component.text("Next"));

        assertThat(spaced.children().get(4).color())
                .isEqualTo(solid.children().get(3).color());
    }

    @Test
    void aBlankTitleIsHandedBackUntouched() {
        Component blank = Component.text(" ");

        assertThat(Gradients.title(blank)).isSameAs(blank);
    }
}

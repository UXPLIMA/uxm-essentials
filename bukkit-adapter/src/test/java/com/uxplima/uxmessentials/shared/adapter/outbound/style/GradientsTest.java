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
        assertThat(title.children().get(0).color()).isEqualTo(StyleTags.accent());
        assertThat(title.children().get(4).color()).isEqualTo(StyleTags.value());
    }

    @Test
    void theTextItselfIsUntouched() {
        assertThat(PlainTextComponentSerializer.plainText().serialize(Gradients.title(NAME)))
                .isEqualTo("Warps");
    }

    @Test
    void aDangerTitleStaysRedAndAMoneyTitleStaysGreen() {
        assertThat(Gradients.title(NAME.color(StyleTags.bad()))
                        .children()
                        .get(0)
                        .color())
                .isEqualTo(StyleTags.bad());
        assertThat(Gradients.title(NAME.color(StyleTags.good()))
                        .children()
                        .get(0)
                        .color())
                .isEqualTo(StyleTags.emerald());
        assertThat(Gradients.title(NAME.color(StyleTags.gold()))
                        .children()
                        .get(0)
                        .color())
                .isEqualTo(StyleTags.gold());
    }

    @Test
    void aColourTheCatalogUsesForReadingIsNeutral() {
        // Ice, white and the greys say nothing about meaning, so a title wearing one opens on the brand sky.
        assertThat(Gradients.title(NAME.color(StyleTags.value()))
                        .children()
                        .get(0)
                        .color())
                .isEqualTo(StyleTags.accent());
        assertThat(Gradients.title(NAME.color(StyleTags.body()))
                        .children()
                        .get(0)
                        .color())
                .isEqualTo(StyleTags.accent());
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

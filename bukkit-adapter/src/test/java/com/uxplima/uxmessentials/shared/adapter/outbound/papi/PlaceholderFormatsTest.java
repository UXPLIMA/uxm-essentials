package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The formatting helpers, which carry their own input in the placeholder key. The point of these tests is
 * that every helper is total: a key that carries something other than a number renders nothing rather than
 * throwing inside a scoreboard refresh.
 */
class PlaceholderFormatsTest {

    @Test
    void groupsThousandsAndDropsTrailingZeros() {
        assertThat(PlaceholderFormats.number("1234567")).contains("1,234,567");
        assertThat(PlaceholderFormats.number("1234567.50")).contains("1,234,567.5");
        assertThat(PlaceholderFormats.number("-2500")).contains("-2,500");
        assertThat(PlaceholderFormats.number("0")).contains("0");
    }

    @Test
    void shortensToTheLargestSuffixThatFits() {
        assertThat(PlaceholderFormats.compact("999")).contains("999");
        assertThat(PlaceholderFormats.compact("1500")).contains("1.5k");
        assertThat(PlaceholderFormats.compact("1234567")).contains("1.23M");
        assertThat(PlaceholderFormats.compact("2000000000")).contains("2B");
        assertThat(PlaceholderFormats.compact("-1500000000000")).contains("-1.5T");
    }

    @Test
    void spellsACountOfSecondsInTheCompactForm() {
        assertThat(PlaceholderFormats.time("3725")).contains("1h2m5s");
        assertThat(PlaceholderFormats.time("0")).contains("0s");
    }

    @Test
    void drawsTheBarToTheAskedWidth() {
        assertThat(PlaceholderFormats.progressBar("5_10_10")).contains("█████░░░░░");
        assertThat(PlaceholderFormats.progressBar("1_4_4")).contains("█░░░");
        assertThat(PlaceholderFormats.progressBar("3_4")).contains("███████████████░░░░░");
    }

    @Test
    void anEmptyOrFullBarNeverOverflowsOrDividesByZero() {
        assertThat(PlaceholderFormats.progressBar("0_10_5")).contains("░░░░░");
        assertThat(PlaceholderFormats.progressBar("50_10_5")).contains("█████");
        assertThat(PlaceholderFormats.progressBar("5_0_5")).contains("░░░░░");
        assertThat(PlaceholderFormats.progressBar("-5_10_5")).contains("░░░░░");
    }

    @Test
    void anAbsurdWidthIsCappedRatherThanDrawn() {
        assertThat(PlaceholderFormats.progressBar("1_2_100000"))
                .hasValueSatisfying(bar -> assertThat(bar).hasSize(100));
    }

    @Test
    void anythingThatIsNotANumberRendersNothing() {
        assertThat(PlaceholderFormats.number("balance")).isEmpty();
        assertThat(PlaceholderFormats.compact("")).isEmpty();
        assertThat(PlaceholderFormats.time("soon")).isEmpty();
        assertThat(PlaceholderFormats.progressBar("half_way")).isEmpty();
        assertThat(PlaceholderFormats.progressBar("1")).isEmpty();
        assertThat(PlaceholderFormats.progressBar("1_2_3_4")).isEmpty();
    }
}

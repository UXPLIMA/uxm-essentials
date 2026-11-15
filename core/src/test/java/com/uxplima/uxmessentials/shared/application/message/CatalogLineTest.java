package com.uxplima.uxmessentials.shared.application.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** A line the plugin writes for itself reads back as the same reference an operator's line would. */
class CatalogLineTest {

    @Test
    void aWrittenLineReadsBackWithItsArguments() {
        CatalogLine line = new CatalogLine("hologram.leaderboard.row", Map.of("rank", "1", "name", "Steve"));

        assertThat(CatalogLine.parse(line.written())).contains(line);
    }

    @Test
    void valuesWithSpacesQuotesAndBackslashesSurviveTheRoundTrip() {
        CatalogLine line =
                new CatalogLine("hologram.leaderboard.row", Map.of("name", "the \"best\" team", "score", "1 234 \\ 5"));

        assertThat(CatalogLine.parse(line.written())).contains(line);
    }

    @Test
    void aLineWithNoArgumentsIsTheBareKey() {
        assertThat(new CatalogLine("hologram.leaderboard.empty", Map.of()).written())
                .isEqualTo("@hologram.leaderboard.empty");
    }
}

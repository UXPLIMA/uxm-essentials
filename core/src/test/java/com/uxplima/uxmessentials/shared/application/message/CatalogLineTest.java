package com.uxplima.uxmessentials.shared.application.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class CatalogLineTest {

    @Test
    void aBareKeyIsACatalogLineWithNoArguments() {
        assertThat(CatalogLine.parse("@custom.click-to-join"))
                .contains(new CatalogLine("custom.click-to-join", Map.of()));
    }

    @Test
    void argumentsFollowTheKeyAsNameValuePairs() {
        assertThat(CatalogLine.parse("@custom.online-players server=skyblock season=1"))
                .contains(new CatalogLine("custom.online-players", Map.of("server", "skyblock", "season", "1")));
    }

    @Test
    void aQuotedValueKeepsItsSpacesAndAnEscapedQuote() {
        assertThat(CatalogLine.parse("@custom.motto text=\"two \\\"quoted\\\" words\" who='the crew'"))
                .contains(new CatalogLine("custom.motto", Map.of("text", "two \"quoted\" words", "who", "the crew")));
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertThat(CatalogLine.parse("  @custom.title   name=x  "))
                .contains(new CatalogLine("custom.title", Map.of("name", "x")));
    }

    @Test
    void aValueMayCarryPlaceholdersAndTags() {
        assertThat(CatalogLine.parse("@custom.count value=%bungee_skyblock% colour=<green>"))
                .contains(new CatalogLine("custom.count", Map.of("value", "%bungee_skyblock%", "colour", "<green>")));
    }

    @Test
    void aLineThatCarriesItsOwnWordsIsNotACatalogLine() {
        assertThat(CatalogLine.parse("<gold>Welcome")).isEmpty();
        assertThat(CatalogLine.parse("")).isEmpty();
        assertThat(CatalogLine.parse("@")).isEmpty();
        assertThat(CatalogLine.parse("mail me @ home")).isEmpty();
    }

    /** Chat-style text that happens to open with an at sign stays the operator's words. */
    @Test
    void proseAfterTheKeyIsNotACatalogLine() {
        assertThat(CatalogLine.parse("@everyone meet at spawn")).isEmpty();
        assertThat(CatalogLine.parse("@custom.x server=")).isEmpty();
        assertThat(CatalogLine.parse("@custom.x text=\"unclosed")).isEmpty();
        assertThat(CatalogLine.parse("@<red>alert")).isEmpty();
    }

    @Test
    void aRepeatedArgumentKeepsTheLastValue() {
        assertThat(CatalogLine.parse("@custom.x a=1 a=2")).contains(new CatalogLine("custom.x", Map.of("a", "2")));
    }
}

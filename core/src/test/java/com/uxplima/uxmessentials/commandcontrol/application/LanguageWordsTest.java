package com.uxplima.uxmessentials.commandcontrol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LanguageWordsTest {

    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");
    private static final Locale ENGLISH = Locale.forLanguageTag("en-US");

    private final LanguageWords words = LanguageWords.of(Map.of(
            "en", List.of("settings", "/Rules"),
            "tr", List.of("ayarlar", "kurallar")));

    @Test
    void eachReaderKeepsOnlyTheWordsOfTheirLanguage() {
        assertThat(words.hiddenFor("settings", TURKISH)).isTrue();
        assertThat(words.hiddenFor("ayarlar", TURKISH)).isFalse();
        assertThat(words.hiddenFor("ayarlar", ENGLISH)).isTrue();
        assertThat(words.hiddenFor("rules", ENGLISH))
                .as("a listed word is read without its slash or case")
                .isFalse();
    }

    @Test
    void aWordInNoListIsEverybodys() {
        assertThat(words.hiddenFor("ping", TURKISH)).isFalse();
        assertThat(words.hiddenFor("ping", ENGLISH)).isFalse();
    }

    @Test
    void aWordGoesWhereItsNamespacedFormGoes() {
        assertThat(words.hiddenFor("uxmessentials:settings", TURKISH)).isTrue();
        assertThat(words.hiddenFor("uxmessentials:ayarlar", TURKISH)).isFalse();
    }

    @Test
    void aWordUnderTwoLanguagesReachesTheReadersOfBoth() {
        LanguageWords shared = LanguageWords.of(Map.of("en", List.of("menu"), "tr", List.of("menu")));

        assertThat(shared.hiddenFor("menu", TURKISH)).isFalse();
        assertThat(shared.hiddenFor("menu", ENGLISH)).isFalse();
        assertThat(shared.hiddenFor("menu", Locale.GERMAN)).isTrue();
    }

    @Test
    void noListsTrimNothing() {
        assertThat(LanguageWords.of(Map.of()).isEmpty()).isTrue();
        assertThat(words.isEmpty()).isFalse();
    }
}

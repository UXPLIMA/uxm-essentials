package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every language describes the same commands as English, each in its own words.
 *
 * <p>A command's description is the {@code describe.<command id>} line of the reader's catalogue, and the code's
 * English where a catalogue has none. Until 2026-09-23 every description was the code's English, so a Turkish
 * player's /help and every usage line read in English. The lines are not message keys of the code: the command id
 * names them, which is why they are held apart from the key parity check and checked here instead.
 */
final class EveryCommandIsDescribedInEveryLanguageTest {

    @Test
    @DisplayName("each shipped language describes exactly the commands English does, none blank, none left in English")
    void everyLanguageDescribesTheSameCommands() {
        Map<String, String> english = CatalogKeys.descriptions("en");
        assertThat(english)
                .describedAs("English describes the plugin's commands")
                .hasSizeGreaterThan(280);
        List<String> wrong = new ArrayList<>();
        for (String language : CatalogKeys.shippedLanguages()) {
            Map<String, String> theirs = CatalogKeys.descriptions(language);
            Set<String> missing = new TreeSet<>(english.keySet());
            missing.removeAll(theirs.keySet());
            Set<String> extra = new TreeSet<>(theirs.keySet());
            extra.removeAll(english.keySet());
            if (!missing.isEmpty()) {
                wrong.add(language + " does not describe " + missing);
            }
            if (!extra.isEmpty()) {
                wrong.add(language + " describes commands English does not: " + extra);
            }
            theirs.forEach((id, line) -> {
                if (line.isBlank()) {
                    wrong.add(language + " leaves " + id + " blank");
                } else if (!language.equals("en") && line.equals(english.get(id))) {
                    wrong.add(language + " writes " + id + " in English");
                }
            });
        }
        assertThat(wrong).isEmpty();
    }
}

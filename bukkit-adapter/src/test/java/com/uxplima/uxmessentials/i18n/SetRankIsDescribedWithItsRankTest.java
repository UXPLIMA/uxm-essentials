package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The description of {@code /setrank} names both arguments it takes.
 *
 * <p>It said {@code /setrank <player>} in every language, and the command refuses that: it needs the rank too.
 */
final class SetRankIsDescribedWithItsRankTest {

    @Test
    @DisplayName("every description of setrank says <player> <rank>")
    void setRankNamesTheRank() {
        List<String> wrong = new ArrayList<>();
        for (String language : CatalogKeys.shippedLanguages()) {
            Map<String, String> lines = CatalogKeys.descriptions(language);
            for (String key : List.of("setrank", "ranks")) {
                if (!lines.get(key).contains("setrank <player> <rank>")) {
                    wrong.add(language + " " + key);
                }
            }
        }
        assertThat(wrong).isEmpty();
    }
}

package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A heading in another language is written in that language.
 *
 * <p>The Turkish window to clear a player warp's password asked "Clear this warp's password?", and nineteen Turkish,
 * fourteen German and fourteen Polish headings were the English ones copied across. Parity cannot see it: the key is
 * there. A heading of two words or more that reads exactly as the English one is a line nobody translated. A single
 * word, such as a game term like "warp", may be the same in both.
 */
final class NoHeadingIsLeftInEnglishTest {

    private static final Pattern HEADING = Pattern.compile("<h:'([^']*)'>");
    private static final Pattern WORD = Pattern.compile("[A-Za-z]{2,}");

    @Test
    @DisplayName("no heading of two words or more is the English one copied")
    void everyHeadingIsTranslated() {
        Map<String, String> english = CatalogKeys.values("en");
        List<String> copied = new ArrayList<>();
        for (String language : CatalogKeys.shippedLanguages()) {
            if (language.equals("en")) {
                continue;
            }
            for (Map.Entry<String, String> line : CatalogKeys.values(language).entrySet()) {
                Matcher heading = HEADING.matcher(line.getValue());
                if (heading.find()
                        && line.getValue().equals(english.get(line.getKey()))
                        && WORD.matcher(heading.group(1)).results().count() >= 2) {
                    copied.add(language + " " + line.getKey() + " = " + heading.group(1));
                }
            }
        }
        assertThat(copied).isEmpty();
    }
}

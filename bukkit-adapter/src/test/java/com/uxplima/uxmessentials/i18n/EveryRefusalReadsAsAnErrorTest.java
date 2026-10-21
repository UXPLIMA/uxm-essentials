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
 * An error line opens with one word in each language, the word that language uses for an error.
 *
 * <p>A refusal reads as an error whichever feature raised it: the red word and the dim triangle are the whole signal.
 * The prefix showed English "error" to every player until 2026-10-21, ignoring what the line wrote, and the lines had
 * gathered feature names there (economy, item, pwarp) that nobody saw. The prefix shows the line's word now, so a
 * feature name in it would turn a refusal into a red feature name; this keeps each language to its one word.
 */
final class EveryRefusalReadsAsAnErrorTest {

    private static final Pattern ERROR_PREFIX = Pattern.compile("<etag:'([^']*)'>");

    /** A line every language writes as an error, whose word is the one the others must carry. */
    private static final String AN_ERROR_LINE = "admin.backup.failed";

    @Test
    @DisplayName("every error line in a language opens with the same error word")
    void everyErrorLineCarriesTheLanguagesErrorWord() {
        List<String> wrong = new ArrayList<>();
        for (String language : CatalogKeys.shippedLanguages()) {
            Map<String, String> lines = CatalogKeys.values(language);
            Matcher reference = ERROR_PREFIX.matcher(lines.get(AN_ERROR_LINE));
            assertThat(reference.find())
                    .describedAs("%s %s is an error line", language, AN_ERROR_LINE)
                    .isTrue();
            String word = reference.group(1);
            for (Map.Entry<String, String> line : lines.entrySet()) {
                Matcher prefix = ERROR_PREFIX.matcher(line.getValue());
                while (prefix.find()) {
                    if (!prefix.group(1).equals(word)) {
                        wrong.add(language + " " + line.getKey() + ": " + prefix.group(1) + ", not " + word);
                    }
                }
            }
        }
        assertThat(wrong).isEmpty();
    }
}

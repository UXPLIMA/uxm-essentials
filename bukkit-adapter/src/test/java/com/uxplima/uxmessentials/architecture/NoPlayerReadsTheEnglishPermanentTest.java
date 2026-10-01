package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A sanction with no end is called permanent in the reader's own language.
 *
 * <p>The moderation use cases filled the {@code duration} and {@code until} placeholders with the English word, so a
 * Turkish player muted for good read "susturuldun (permanent)", though every catalogue translates the word. The use
 * cases compose what a player reads; the audit sinks outside them write a log, and may still carry it.
 */
final class NoPlayerReadsTheEnglishPermanentTest {

    private static final Pattern ENGLISH = Pattern.compile("orElse\\(\"permanent\"\\)");

    @Test
    @DisplayName("no message placeholder falls back to the English word permanent")
    void permanentIsTranslated() {
        List<String> english = new ArrayList<>();
        int useCases = 0;
        for (Path file : ProductionSources.files()) {
            // The use cases compose what a player reads. The audit sinks write a log, in English on purpose.
            if (!file.toString().contains("/core/src/main/java/")
                    || !file.toString().contains("/application/")) {
                continue;
            }
            useCases++;
            Matcher matcher = ENGLISH.matcher(ProductionSources.read(file));
            while (matcher.find()) {
                english.add(file.getFileName().toString());
            }
        }
        assertThat(useCases).describedAs("the use cases are read").isGreaterThan(100);
        assertThat(english).isEmpty();
    }
}

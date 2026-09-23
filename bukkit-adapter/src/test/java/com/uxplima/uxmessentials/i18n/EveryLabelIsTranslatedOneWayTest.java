package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every language names each label of a line one way, in its own words and in capitals.
 *
 * <p>A label is the word before the arrow of a line: {@code <tag:'...'>} for a module, {@code <etag:'...'>} for a
 * failure. On 2026-09-23 every one of the eleven translated catalogues still carried the 47 English labels, so a
 * Turkish player read {@code ECONOMY} before a Turkish sentence. A label is a word a translator moves: only the
 * words that read the same in every language, a brand, an abbreviation or the warp every server says, may stay as
 * English writes them, with the few words a language spells as English does. The
 * brand in {@code prefix} is a name and is not read here.
 */
final class EveryLabelIsTranslatedOneWayTest {

    private static final Pattern LABEL = Pattern.compile("<(e?tag):'([^']*)'>");

    /** Written the same in every language: an abbreviation, a brand, or the game's own word for a place. */
    private static final Set<String> SHARED = Set.of("2FA", "AFK", "DISCORD", "NPC", "WARP", "PWARP");

    /** A word the language itself spells the way English does, which is its own word and not a borrowing. */
    private static final Map<String, Set<String>> SPELLED_ALIKE = Map.of(
            "de", Set.of("BANK"),
            "fr", Set.of("MENU", "VOTE"),
            "pl", Set.of("BANK", "MENU"),
            "pt", Set.of("MENU"));

    @Test
    @DisplayName("each English label is written one way in every other language, in capitals, and never borrowed")
    void everyLabelIsTranslatedOneWay() {
        Map<String, String> english = CatalogKeys.values("en");
        List<String> wrong = new ArrayList<>();
        int compared = 0;
        for (String language : CatalogKeys.shippedLanguages()) {
            if (language.equals("en")) {
                continue;
            }
            Locale locale = Locale.forLanguageTag(language);
            Map<String, Set<String>> ways = new TreeMap<>();
            for (Map.Entry<String, String> line : CatalogKeys.values(language).entrySet()) {
                List<String> theirs = labels(line.getValue());
                List<String> ours = labels(english.getOrDefault(line.getKey(), ""));
                for (String label : theirs) {
                    String bare = label.substring(label.indexOf(':') + 1);
                    if (!bare.equals(bare.toUpperCase(locale))) {
                        wrong.add(language + " writes " + bare + " in lower case at " + line.getKey());
                    }
                }
                if (theirs.size() != ours.size()) {
                    continue;
                }
                for (int i = 0; i < ours.size(); i++) {
                    String bare = ours.get(i).substring(ours.get(i).indexOf(':') + 1);
                    if (theirs.get(i).equals(ours.get(i))
                            && !SHARED.contains(bare)
                            && !SPELLED_ALIKE.getOrDefault(language, Set.of()).contains(bare)) {
                        wrong.add(language + " borrows " + bare + " at " + line.getKey());
                    }
                    ways.computeIfAbsent(ours.get(i), key -> new LinkedHashSet<>())
                            .add(theirs.get(i));
                    compared++;
                }
            }
            ways.forEach((label, written) -> {
                if (written.size() > 1) {
                    wrong.add(language + " writes " + label + " as " + written);
                }
            });
        }

        assertThat(compared)
                .describedAs("a scan that paired no label read nothing")
                .isPositive();
        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("English writes its own labels in capitals, the way it draws them")
    void englishWritesItsLabelsInCapitals() {
        List<String> wrong = new ArrayList<>();
        CatalogKeys.values("en").forEach((key, line) -> {
            for (String label : labels(line)) {
                String bare = label.substring(label.indexOf(':') + 1);
                if (!bare.equals(bare.toUpperCase(Locale.ENGLISH))) {
                    wrong.add(bare + " at " + key);
                }
            }
        });
        assertThat(wrong).isEmpty();
    }

    private static List<String> labels(String line) {
        List<String> found = new ArrayList<>();
        Matcher matcher = LABEL.matcher(line);
        while (matcher.find()) {
            found.add(matcher.group(1) + ":" + matcher.group(2));
        }
        return found;
    }
}

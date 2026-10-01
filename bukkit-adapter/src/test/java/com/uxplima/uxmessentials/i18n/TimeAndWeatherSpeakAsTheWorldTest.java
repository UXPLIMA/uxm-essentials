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
 * {@code /day}, {@code /night}, {@code /sun} and {@code /rain} answer under the world's tag.
 *
 * <p>They answered under the item tag, because the context that owns them also owns {@code /give} and {@code /hat}: an
 * operator read "ITEM ▶ set the time in world to 1000".
 */
final class TimeAndWeatherSpeakAsTheWorldTest {

    private static final List<String> TIME_AND_WEATHER =
            List.of("itemworld.time.set", "itemworld.weather.set", "itemworld.bad-time", "itemworld.bad-weather");
    private static final String A_WORLD_LINE = "world.created";
    private static final Pattern TAG = Pattern.compile("^<e?tag:'([^']*)'>");

    @Test
    @DisplayName("the time and weather lines carry the tag the world lines carry, in every language")
    void timeAndWeatherCarryTheWorldTag() {
        List<String> wrong = new ArrayList<>();
        for (String language : CatalogKeys.shippedLanguages()) {
            Map<String, String> lines = CatalogKeys.values(language);
            String world = tag(lines.get(A_WORLD_LINE));
            for (String key : TIME_AND_WEATHER) {
                if (!world.equals(tag(lines.get(key)))) {
                    wrong.add(language + " " + key);
                }
            }
        }
        assertThat(wrong).isEmpty();
    }

    private static String tag(String line) {
        Matcher tag = TAG.matcher(line);
        assertThat(tag.find()).describedAs("a tagged line: %s", line).isTrue();
        return tag.group(1);
    }
}

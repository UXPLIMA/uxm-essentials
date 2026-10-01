package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An apostrophe is written once.
 *
 * <p>Twenty nine lines doubled it, the way a Java MessageFormat pattern escapes one. Nothing here reads a line as a
 * MessageFormat pattern, and MiniMessage keeps both, inside a quoted argument too: a player typing {@code /skull} read
 * "gave you Tester''s skull". Inside a quoted tag argument the line uses a typographic apostrophe, which needs no
 * escape in either MiniMessage or HOCON.
 */
final class NoLineDoublesAnApostropheTest {

    @Test
    @DisplayName("no line of any shipped language doubles an apostrophe")
    void noLineDoublesAnApostrophe() {
        List<String> doubled = new ArrayList<>();
        for (String language : CatalogKeys.shippedLanguages()) {
            for (Map.Entry<String, String> line : CatalogKeys.values(language).entrySet()) {
                if (line.getValue().contains("''")) {
                    doubled.add(language + " " + line.getKey());
                }
            }
        }
        assertThat(doubled).isEmpty();
    }
}

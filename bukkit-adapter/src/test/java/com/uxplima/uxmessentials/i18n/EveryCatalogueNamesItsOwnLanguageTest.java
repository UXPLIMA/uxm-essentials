package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every catalogue's {@code lang.code} line is the code of the file it is in.
 *
 * <p>A name an operator writes per language, a warp picker's preset among them, is chosen by this line. A Turkish
 * catalogue that said {@code en} would hand a Turkish reader the English name, and nothing on screen would say why.
 */
final class EveryCatalogueNamesItsOwnLanguageTest {

    @Test
    @DisplayName("each shipped catalogue names the language its file is written in")
    void everyCatalogueNamesItsOwnLanguage() {
        List<String> wrong = new ArrayList<>();
        for (String language : CatalogKeys.shippedLanguages()) {
            String written = CatalogKeys.value(language, SharedMessageKey.LANG_CODE.key());
            if (!written.equals(language)) {
                wrong.add("messages_" + language + ".conf says " + written);
            }
        }
        assertThat(CatalogKeys.shippedLanguages()).hasSizeGreaterThan(2);
        assertThat(wrong).isEmpty();
    }
}

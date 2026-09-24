package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmlib.menu.MenuKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every word the window engine asks this plugin for is a key of ours and a line of every language.
 *
 * <p>The engine draws a few windows nobody wrote a file for, and names a key for each word on them. The other
 * plugins read the library's own words underneath their files; this one builds its catalogue itself, so a key the
 * engine names and this plugin does not write reads as its key. uxm-plots found the question a Bedrock form asks
 * when one tile does more than one thing reading as {@code gui.gesture.title}, and this plugin had the same hole.
 */
final class EveryEngineWordIsWrittenTest {

    @Test
    @DisplayName("each key the engine names is a GuiMessageKey and a line of every shipped language")
    void everyEngineWordIsWritten() throws IllegalAccessException {
        Set<String> asked = new TreeSet<>();
        for (Field field : MenuKeys.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.get(null) instanceof String key) {
                asked.add(key);
            }
        }
        assertThat(asked)
                .describedAs("the engine names its words; finding none is a broken read")
                .hasSizeGreaterThan(20);

        Set<String> ours = new TreeSet<>();
        for (GuiMessageKey key : GuiMessageKey.values()) {
            ours.add(key.key());
        }
        Set<String> unheld = new TreeSet<>(asked);
        unheld.removeAll(ours);
        assertThat(unheld).describedAs("hold these in GuiMessageKey").isEmpty();

        List<String> unwritten = new ArrayList<>();
        for (String language : CatalogKeys.shippedLanguages()) {
            Set<String> written = CatalogKeys.read(language);
            for (String key : asked) {
                if (!written.contains(key)) {
                    unwritten.add(language + " " + key);
                }
            }
        }
        assertThat(unwritten).isEmpty();
    }
}

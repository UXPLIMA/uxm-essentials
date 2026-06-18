package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

class HologramSymbolsTest {

    @Test
    void appliesEveryConfiguredReplacement() {
        HologramSymbols symbols = new HologramSymbols(Map.of(":heart:", "<red>❤", ":star:", "<gold>★"));

        assertThat(symbols.apply("hp :heart: lvl :star:")).isEqualTo("hp <red>❤ lvl <gold>★");
    }

    @Test
    void leavesTextWithoutTokensUntouched() {
        HologramSymbols symbols = new HologramSymbols(Map.of(":heart:", "<red>❤"));

        assertThat(symbols.apply("plain line")).isEqualTo("plain line");
    }

    @Test
    void wrapReturnsTheNextTransformUnchangedWhenNoSymbols() {
        UnaryOperator<String> next = source -> source + "!";

        assertThat(HologramSymbols.none().wrap(next)).isSameAs(next);
    }

    @Test
    void wrapAppliesSymbolsBeforeTheNextTransform() {
        HologramSymbols symbols = new HologramSymbols(Map.of(":heart:", "HEART"));
        // next upper-cases, so seeing HEART (not :heart:) proves the symbol expanded first.
        UnaryOperator<String> wrapped = symbols.wrap(source -> source.toUpperCase(java.util.Locale.ROOT));

        assertThat(wrapped.apply(":heart: x")).isEqualTo("HEART X");
    }

    @Test
    void replacementsApplyInDeclarationOrderSoTheyCanChain() {
        Map<String, String> ordered = new LinkedHashMap<>();
        ordered.put(":a:", ":b:");
        ordered.put(":b:", "done");
        HologramSymbols symbols = new HologramSymbols(ordered);

        assertThat(symbols.apply(":a:")).isEqualTo("done");
    }

    @Test
    void fromConfigReadsTheSymbolsMapAndSkipsABlankToken() {
        HologramSymbols symbols = HologramSymbols.fromConfig(new MapConfigStore(
                List.of(":heart:", "   "), Map.of("symbols.:heart:", "<red>❤", "symbols.   ", "ignored")));

        assertThat(symbols.apply(":heart:")).isEqualTo("<red>❤");
        assertThat(symbols.replacements()).containsOnlyKeys(":heart:");
    }

    @Test
    void fromConfigOnAnEmptyStoreHasNoSymbols() {
        assertThat(HologramSymbols.fromConfig(new MapConfigStore(List.of(), Map.of()))
                        .isEmpty())
                .isTrue();
    }

    private record MapConfigStore(List<String> symbolKeys, Map<String, String> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.getOrDefault(path, fallback);
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }

        @Override
        public List<String> getKeys(String path) {
            return path.equals("symbols") ? symbolKeys : List.of();
        }
    }
}

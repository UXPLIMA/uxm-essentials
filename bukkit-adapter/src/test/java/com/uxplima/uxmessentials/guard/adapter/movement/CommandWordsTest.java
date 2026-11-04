package com.uxplima.uxmessentials.guard.adapter.movement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CommandWordsTest {

    /**
     * Names as Paper reports them: the command entry lists its aliases, an alias entry knows only itself, and a
     * namespaced word resolves like the bare one.
     */
    private static final Map<String, Set<String>> COMMANDS = Map.of(
            "home", Set.of("h", "homes"),
            "warp", Set.of("wp", "warps"),
            "guard", Set.of("bolge"));

    private static Set<String> names(String word) {
        String bare = CommandWords.bare(word);
        Set<String> names = new HashSet<>(COMMANDS.getOrDefault(bare, Set.of()));
        names.add(bare);
        return names;
    }

    @Test
    void anEntryCatchesTheCommandTypedByAnyAlias() {
        assertThat(CommandWords.matches("spawn, home", "h", "", CommandWordsTest::names))
                .isTrue();
        assertThat(CommandWords.matches("spawn, home", "essentials:home", "", CommandWordsTest::names))
                .isTrue();
        assertThat(CommandWords.matches("spawn, home", "wp", "", CommandWordsTest::names))
                .isFalse();
    }

    @Test
    void anEntryWrittenAsAnAliasCatchesTheCommandTypedByName() {
        assertThat(CommandWords.matches("h", "home", "base", CommandWordsTest::names))
                .isTrue();
    }

    @Test
    void anEntryWithArgumentsNeedsThoseLeadingArguments() {
        assertThat(CommandWords.matches("warp_vip", "wp", "vip", CommandWordsTest::names))
                .isTrue();
        assertThat(CommandWords.matches("warp_vip", "warp", "vip now", CommandWordsTest::names))
                .isTrue();
        assertThat(CommandWords.matches("warp_vip", "warp", "vipshop", CommandWordsTest::names))
                .isFalse();
        assertThat(CommandWords.matches("warp_vip", "warp", "", CommandWordsTest::names))
                .isFalse();
    }

    @Test
    void aCommandIsRecognisedByAnyOfItsNames() {
        assertThat(CommandWords.same("bolge", "guard", CommandWordsTest::names)).isTrue();
        assertThat(CommandWords.same("warp", "guard", CommandWordsTest::names)).isFalse();
    }

    @Test
    void emptyEntriesNeverMatch() {
        assertThat(CommandWords.matches(" , ,", "home", "", CommandWordsTest::names))
                .isFalse();
    }
}

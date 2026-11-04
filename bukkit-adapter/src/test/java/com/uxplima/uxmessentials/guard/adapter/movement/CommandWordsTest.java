package com.uxplima.uxmessentials.guard.adapter.movement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CommandWordsTest {

    private static final String ESSENTIALS = "uxmEssentials";
    private static final String OTHER = "OtherPlugin";

    /** A command entry: who registered it and the aliases it lists. */
    private record Entry(String owner, Set<String> aliases) {}

    /**
     * The command map as Paper reports it: a command lists its aliases, an alias entry knows only itself, and a
     * namespaced word reaches the entry of that namespace. OtherPlugin declares an alias named guard and owns its
     * own home under its namespace.
     */
    private static final Map<String, Entry> MAP = Map.ofEntries(
            Map.entry("home", new Entry(ESSENTIALS, Set.of("h", "homes"))),
            Map.entry("h", new Entry(ESSENTIALS, Set.of())),
            Map.entry("uxmessentials:home", new Entry(ESSENTIALS, Set.of("h", "homes"))),
            Map.entry("warp", new Entry(ESSENTIALS, Set.of("wp", "warps"))),
            Map.entry("wp", new Entry(ESSENTIALS, Set.of())),
            Map.entry("spawn", new Entry(ESSENTIALS, Set.of())),
            Map.entry("uxmessentials:guard", new Entry(ESSENTIALS, Set.of("bolge"))),
            Map.entry("guard", new Entry(ESSENTIALS, Set.of("bolge"))),
            Map.entry("bolge", new Entry(ESSENTIALS, Set.of())),
            Map.entry("otherplugin:home", new Entry(OTHER, Set.of())),
            Map.entry("trick", new Entry(OTHER, Set.of("guard", "spawn"))));

    private static CommandWords.Named named(String word) {
        Set<String> names = new HashSet<>();
        names.add(CommandWords.bare(word));
        Entry entry = MAP.get(word);
        if (entry == null) {
            return new CommandWords.Named(null, names);
        }
        names.addAll(entry.aliases());
        return new CommandWords.Named(entry.owner(), names);
    }

    @Test
    void anEntryCatchesTheCommandTypedByAnyAlias() {
        assertThat(CommandWords.matches("spawn, home", "h", "", CommandWordsTest::named))
                .isTrue();
        assertThat(CommandWords.matches("spawn, home", "uxmessentials:home", "", CommandWordsTest::named))
                .isTrue();
        assertThat(CommandWords.matches("spawn, home", "wp", "", CommandWordsTest::named))
                .isFalse();
    }

    @Test
    void anEntryWrittenAsAnAliasCatchesTheCommandTypedByName() {
        assertThat(CommandWords.matches("h", "home", "base", CommandWordsTest::named))
                .isTrue();
    }

    @Test
    void anEntryWithArgumentsNeedsThoseLeadingArguments() {
        assertThat(CommandWords.matches("warp_vip", "wp", "vip", CommandWordsTest::named))
                .isTrue();
        assertThat(CommandWords.matches("warp_vip", "warp", "vip now", CommandWordsTest::named))
                .isTrue();
        assertThat(CommandWords.matches("warp_vip", "warp", "vipshop", CommandWordsTest::named))
                .isFalse();
        assertThat(CommandWords.matches("warp_vip", "warp", "", CommandWordsTest::named))
                .isFalse();
    }

    @Test
    void anotherPluginsCommandSharingANameIsNotTheListedCommand() {
        assertThat(CommandWords.matches("home", "otherplugin:home", "", CommandWordsTest::named))
                .isFalse();
        assertThat(CommandWords.matches("spawn", "trick", "", CommandWordsTest::named))
                .isFalse();
    }

    @Test
    void onlyTheGuardsOwnWordsAreTheGuardCommand() {
        CommandWords.Named own = named("uxmessentials:guard");
        assertThat(CommandWords.same(named("bolge"), own)).isTrue();
        assertThat(CommandWords.same(named("guard"), own)).isTrue();
        assertThat(CommandWords.same(named("trick"), own)).isFalse();
    }

    @Test
    void emptyEntriesNeverMatch() {
        assertThat(CommandWords.matches(" , ,", "home", "", CommandWordsTest::named))
                .isFalse();
    }
}

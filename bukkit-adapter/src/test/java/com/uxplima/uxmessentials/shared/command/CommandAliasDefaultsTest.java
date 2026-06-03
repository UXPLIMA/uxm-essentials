package com.uxplima.uxmessentials.shared.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.uxmessentials.bootstrap.CommandAliasDefaults;
import com.uxplima.uxmessentials.shared.application.command.CommandDefinition;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import org.junit.jupiter.api.Test;

/**
 * Covers the curated muscle-memory alias table that augments each command's code-side defaults before
 * the {@link com.uxplima.uxmessentials.shared.application.command.CommandCatalog} resolves them. The
 * table must add to, never replace, code defaults; must not duplicate; and must never alias the
 * standalone commands ({@code homes}/{@code warps}/{@code kits}, the time/weather literals) onto another
 * command.
 */
class CommandAliasDefaultsTest {

    private static CommandDefinition def(String id, String name, String... aliases) {
        return new CommandDefinition(new CommandId(id), name, List.of(aliases));
    }

    @Test
    void augment_addsMuscleMemoryAliasForKnownCommand() {
        List<CommandDefinition> out = CommandAliasDefaults.augment(List.of(def("reply", "reply")));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).defaultAliases()).contains("r");
    }

    @Test
    void augment_preservesExistingCodeAliases() {
        List<CommandDefinition> out = CommandAliasDefaults.augment(List.of(def("balance", "balance", "bal", "money")));

        assertThat(out.get(0).defaultAliases()).containsAll(List.of("bal", "money"));
    }

    @Test
    void augment_doesNotDuplicate() {
        // "money" is both a code default and a curated alias for balance; it must appear exactly once.
        List<CommandDefinition> out = CommandAliasDefaults.augment(List.of(def("balance", "balance", "bal", "money")));

        assertThat(out.get(0).defaultAliases()).filteredOn("money"::equals).hasSize(1);
    }

    @Test
    void augment_leavesUnknownCommandsUntouched() {
        List<CommandDefinition> out = CommandAliasDefaults.augment(List.of(def("somefuture", "somefuture")));

        assertThat(out.get(0).defaultAliases()).isEmpty();
    }

    @Test
    void augment_neverAliasesStandaloneConflictTargets() {
        List<CommandDefinition> out =
                CommandAliasDefaults.augment(List.of(def("home", "home"), def("warp", "warp"), def("kit", "kit")));

        assertThat(out)
                .flatMap(CommandDefinition::defaultAliases)
                .doesNotContain(
                        "homes", "warps", "kits", "day", "night", "sun", "rain", "thunder", "walkspeed", "flyspeed");
    }

    @Test
    void augment_addsEssentialsXMuscleMemoryAliases() {
        List<CommandDefinition> out = CommandAliasDefaults.augment(List.of(
                def("feed", "feed"), def("back", "back"), def("afk", "afk"), def("god", "god"), def("near", "near")));

        assertThat(out.get(0).defaultAliases()).contains("eat");
        assertThat(out.get(1).defaultAliases()).contains("return");
        assertThat(out.get(2).defaultAliases()).contains("away");
        assertThat(out.get(3).defaultAliases()).contains("godmode");
        assertThat(out.get(4).defaultAliases()).contains("nearby");
    }

    @Test
    void augment_isDeterministic() {
        List<CommandDefinition> first =
                CommandAliasDefaults.augment(List.of(def("balance", "balance", "bal", "money")));
        List<CommandDefinition> second =
                CommandAliasDefaults.augment(List.of(def("balance", "balance", "bal", "money")));

        assertThat(first.get(0).defaultAliases())
                .containsExactlyElementsOf(second.get(0).defaultAliases());
    }
}

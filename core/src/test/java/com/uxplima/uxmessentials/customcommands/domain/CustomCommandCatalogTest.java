package com.uxplima.uxmessentials.customcommands.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.customcommands.domain.ActionChain.ChainLimits;
import org.junit.jupiter.api.Test;

class CustomCommandCatalogTest {

    private static final ChainLimits LIMITS = ChainLimits.defaults();

    private static CustomCommand command(String id, String name, String... aliases) {
        return command(id, new CommandLiteral(name, List.of(aliases), Map.of()));
    }

    private static CustomCommand command(String id, CommandLiteral literal) {
        return new CustomCommand(
                CustomCommandId.of(id),
                literal,
                Optional.empty(),
                Optional.empty(),
                true,
                "test command",
                Optional.empty(),
                Duration.ZERO,
                Duration.ZERO,
                0.0,
                List.of(),
                List.of(),
                ActionChain.empty(),
                ActionChain.of(List.of("message:hi"), LIMITS));
    }

    @Test
    void keepsDeclarationOrderAndResolvesById() {
        CustomCommandCatalog.Loaded loaded = CustomCommandCatalog.of(List.of(command("b", "b"), command("a", "a")));

        assertThat(loaded.ids()).containsExactly("b", "a");
        assertThat(loaded.byId("a")).isPresent();
        assertThat(loaded.byId("missing")).isEmpty();
    }

    @Test
    void theFirstClaimOfALiteralKeepsItAndTheSecondIsDroppedWithAWarning() {
        CustomCommandCatalog.Loaded loaded =
                CustomCommandCatalog.of(List.of(command("first", "shop"), command("second", "shop")));

        assertThat(loaded.ids()).containsExactly("first");
        assertThat(loaded.warnings()).anyMatch(warning -> warning.contains("second"));
    }

    @Test
    void anAliasThatCollidesWithAnEarlierLiteralIsDroppedRatherThanTheWholeCommand() {
        CustomCommandCatalog.Loaded loaded =
                CustomCommandCatalog.of(List.of(command("first", "shop"), command("second", "store", "shop")));

        assertThat(loaded.ids()).containsExactly("first", "second");
        assertThat(loaded.byId("second").orElseThrow().literal().aliases()).isEmpty();
        assertThat(loaded.warnings()).anyMatch(warning -> warning.contains("shop"));
    }

    @Test
    void aLocalizedAliasCollisionDropsOnlyThatWord() {
        CustomCommand turkish =
                command("second", new CommandLiteral("store", List.of(), Map.of("tr", List.of("shop", "dukkan"))));
        CustomCommandCatalog.Loaded loaded = CustomCommandCatalog.of(List.of(command("first", "shop"), turkish));

        assertThat(loaded.byId("second").orElseThrow().literal().localizedAliases())
                .containsEntry("tr", List.of("dukkan"));
    }

    @Test
    void duplicateIdsCannotBothLoad() {
        CustomCommandCatalog.Loaded loaded = CustomCommandCatalog.of(List.of(command("a", "a"), command("a", "b")));

        assertThat(loaded.ids()).containsExactly("a");
        assertThat(loaded.warnings()).isNotEmpty();
    }

    @Test
    void listsEveryClaimedWordOnceForTabCompletion() {
        CustomCommandCatalog.Loaded loaded =
                CustomCommandCatalog.of(List.of(command("first", "shop", "store"), command("second", "kit")));

        assertThat(loaded.words()).containsExactly("shop", "store", "kit");
    }
}

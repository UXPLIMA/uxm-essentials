package com.uxplima.uxmessentials.shared.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CommandCatalogResolveTest {

    private static CommandDefinition def(String id, String name, String... aliases) {
        return new CommandDefinition(new CommandId(id), name, List.of(aliases));
    }

    private static CommandOverride ov(boolean enabled, Optional<String> name, List<String> aliases) {
        return new CommandOverride(enabled, name, aliases, Optional.empty());
    }

    @Test
    void missingOverrideFallsBackToDefault() {
        var res = CommandCatalog.resolve(List.of(def("home", "home", "h")), Map.of(), true);
        assertThat(res.effective()).singleElement().satisfies(e -> {
            assertThat(e.name()).isEqualTo("home");
            assertThat(e.aliases()).containsExactly("h");
            assertThat(e.enabled()).isTrue();
        });
    }

    @Test
    void overrideWinsForNameAndAliases() {
        var ov = Map.of("home", ov(true, Optional.of("ev"), List.of("e", "h")));
        var res = CommandCatalog.resolve(List.of(def("home", "home", "h")), ov, true);
        var e = res.effective().get(0);
        assertThat(e.name()).isEqualTo("ev");
        assertThat(e.aliases()).containsExactly("e", "h");
    }

    @Test
    void disabledCommandIsMarkedNotEnabledAndClaimsNothing() {
        var ov = Map.of("home", ov(false, Optional.empty(), List.of()));
        var res = CommandCatalog.resolve(List.of(def("home", "home")), ov, true);
        assertThat(res.effective().get(0).enabled()).isFalse();
        assertThat(res.effective().get(0).aliases()).isEmpty();
    }

    @Test
    void blankNameFallsBackWithWarning() {
        var ov = Map.of("home", ov(true, Optional.of("  "), List.of()));
        var res = CommandCatalog.resolve(List.of(def("home", "home")), ov, true);
        assertThat(res.effective().get(0).name()).isEqualTo("home");
        assertThat(res.warnings()).isNotEmpty();
    }

    @Test
    void collidingAliasIsDroppedFromTheSecondCommandWithWarning() {
        var res = CommandCatalog.resolve(List.of(def("home", "home", "h"), def("help", "help", "h")), Map.of(), true);
        assertThat(res.effective().get(0).aliases()).contains("h");
        assertThat(res.effective().get(1).aliases()).doesNotContain("h");
        assertThat(res.warnings()).isNotEmpty();
    }

    @Test
    void collidingPrimaryNameFallsBackToDefaultName() {
        var ov = Map.of(
                "home", ov(true, Optional.of("x"), List.of()),
                "help", ov(true, Optional.of("x"), List.of()));
        var res = CommandCatalog.resolve(List.of(def("home", "home"), def("help", "help")), ov, true);
        assertThat(res.effective().get(0).name()).isEqualTo("x");
        assertThat(res.effective().get(1).name()).isEqualTo("help");
        assertThat(res.warnings()).isNotEmpty();
    }

    @Test
    void guiFallsBackToGlobalDefaultWhenNoOverride() {
        var on = CommandCatalog.resolve(List.of(def("home", "home")), Map.of(), true);
        var off = CommandCatalog.resolve(List.of(def("home", "home")), Map.of(), false);
        assertThat(on.effective().get(0).gui()).isTrue();
        assertThat(off.effective().get(0).gui()).isFalse();
    }

    @Test
    void guiFallsBackToGlobalDefaultWhenOverrideLeavesItEmpty() {
        var ov = Map.of("home", new CommandOverride(true, Optional.empty(), List.of(), Optional.empty()));
        var res = CommandCatalog.resolve(List.of(def("home", "home")), ov, false);
        assertThat(res.effective().get(0).gui()).isFalse();
    }

    @Test
    void guiOverrideWinsOverGlobalDefault() {
        var off = Map.of("home", new CommandOverride(true, Optional.empty(), List.of(), Optional.of(false)));
        var on = Map.of("warp", new CommandOverride(true, Optional.empty(), List.of(), Optional.of(true)));
        assertThat(CommandCatalog.resolve(List.of(def("home", "home")), off, true)
                        .effective()
                        .get(0)
                        .gui())
                .isFalse();
        assertThat(CommandCatalog.resolve(List.of(def("warp", "warp")), on, false)
                        .effective()
                        .get(0)
                        .gui())
                .isTrue();
    }

    @Test
    void disabledCommandStillCarriesAResolvedGuiValue() {
        var ov = Map.of("home", new CommandOverride(false, Optional.empty(), List.of(), Optional.of(true)));
        var res = CommandCatalog.resolve(List.of(def("home", "home")), ov, false);
        assertThat(res.effective().get(0).enabled()).isFalse();
        assertThat(res.effective().get(0).gui()).isTrue();
    }

    @Test
    void unicodeLocalizedAliasesAreRegisteredWithoutReplacingCanonicalAliases() {
        CommandOverride home = new CommandOverride(
                true,
                Optional.empty(),
                List.of("h"),
                Optional.empty(),
                Map.of("tr_TR", List.of("ev", "yuvam"), "de", List.of("zuhause")));

        EffectiveCommand effective = CommandCatalog.resolve(List.of(def("home", "home")), Map.of("home", home), true)
                .effective()
                .get(0);

        assertThat(effective.name()).isEqualTo("home");
        assertThat(effective.aliases()).containsExactly("h");
        assertThat(effective.localizedAliases().get("tr-tr")).containsExactly("ev", "yuvam");
        assertThat(effective.localizedAliases().get("de")).containsExactly("zuhause");
        assertThat(effective.registrationAliases()).containsExactlyInAnyOrder("h", "ev", "yuvam", "zuhause");
    }

    @Test
    void sharedLocalizedAliasCanBelongToMultipleLocalesOfTheSameCommand() {
        CommandOverride home = new CommandOverride(
                true, Optional.empty(), List.of(), Optional.empty(), Map.of("tr", List.of("ev"), "az", List.of("ev")));

        EffectiveCommand effective = CommandCatalog.resolve(List.of(def("home", "home")), Map.of("home", home), true)
                .effective()
                .get(0);

        assertThat(effective.localizedAliases().get("tr")).containsExactly("ev");
        assertThat(effective.localizedAliases().get("az")).containsExactly("ev");
        assertThat(effective.registrationAliases()).containsExactly("ev");
    }

    @Test
    void localizedAliasCollisionAcrossCommandsIsDroppedDeterministically() {
        CommandOverride home =
                new CommandOverride(true, Optional.empty(), List.of(), Optional.empty(), Map.of("tr", List.of("ev")));
        CommandOverride warp =
                new CommandOverride(true, Optional.empty(), List.of(), Optional.empty(), Map.of("tr", List.of("ev")));

        CommandCatalog.Resolution result = CommandCatalog.resolve(
                List.of(def("home", "home"), def("warp", "warp")), Map.of("home", home, "warp", warp), true);

        assertThat(result.effective().get(0).localizedAliases().get("tr")).containsExactly("ev");
        assertThat(result.effective().get(1).localizedAliases()).isEmpty();
        assertThat(result.warnings()).anyMatch(warning -> warning.message().contains("collides"));
    }

    @Test
    void invalidLocaleAndWhitespaceAliasAreDroppedButUnicodeLiteralSurvives() {
        CommandOverride spawn = new CommandOverride(
                true,
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Map.of("???", List.of("nope"), "tr", List.of("başlangıç", "iki kelime", "/doğma")));

        CommandCatalog.Resolution result =
                CommandCatalog.resolve(List.of(def("spawn", "spawn")), Map.of("spawn", spawn), true);

        assertThat(result.effective().get(0).localizedAliases().get("tr")).containsExactly("başlangıç");
        assertThat(result.warnings()).hasSize(3);
    }
}

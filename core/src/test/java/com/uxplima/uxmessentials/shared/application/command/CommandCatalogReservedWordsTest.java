package com.uxplima.uxmessentials.shared.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A word another plugin of ours owns, and is installed to answer, is not ours to register.
 *
 * <p>uxmShop ships {@code /sell} and {@code /worth}, uxmGlow ships {@code /glow}, and this plugin ships all three
 * as well. Two plugins registering one word are resolved by load order, so on the oneblock setup on 2026-09-22
 * {@code /sell} was the shop's and {@code /worth} was ours, each reading its own price table. The dedicated
 * plugin owns its word when it is installed; without it, this plugin keeps the word.
 */
class CommandCatalogReservedWordsTest {

    private static CommandDefinition def(String id, String name, String... aliases) {
        return new CommandDefinition(new CommandId(id), name, List.of(aliases));
    }

    @Test
    @DisplayName("a command whose name another installed plugin owns stays off and says who owns it")
    void aReservedNameLeavesTheCommandOff() {
        var res = CommandCatalog.resolve(List.of(def("sell", "sell")), Map.of(), true, Map.of("sell", "uxmShop"));

        assertThat(res.effective().get(0).enabled()).isFalse();
        assertThat(res.warnings()).singleElement().asString().contains("sell", "uxmShop");
    }

    @Test
    @DisplayName("an alias another installed plugin owns is dropped and the command keeps its own name")
    void aReservedAliasIsDropped() {
        var res = CommandCatalog.resolve(
                List.of(def("worth", "value", "worth", "price")), Map.of(), true, Map.of("worth", "uxmShop"));

        var effective = res.effective().get(0);
        assertThat(effective.enabled()).isTrue();
        assertThat(effective.name()).isEqualTo("value");
        assertThat(effective.aliases()).containsExactly("price");
        assertThat(res.warnings()).singleElement().asString().contains("worth", "uxmShop");
    }

    @Test
    @DisplayName("an operator who renames the command gets it back under the new word")
    void aRenameTakesTheCommandBack() {
        var overrides = Map.of("sell", new CommandOverride(true, Optional.of("esell"), List.of(), Optional.empty()));

        var res = CommandCatalog.resolve(List.of(def("sell", "sell")), overrides, true, Map.of("sell", "uxmShop"));

        assertThat(res.effective().get(0).enabled()).isTrue();
        assertThat(res.effective().get(0).name()).isEqualTo("esell");
    }

    @Test
    @DisplayName("with nothing reserved the catalogue resolves exactly as it always did")
    void nothingReservedChangesNothing() {
        var defs = List.of(def("sell", "sell", "s"), def("glow", "glow"));

        assertThat(CommandCatalog.resolve(defs, Map.of(), true, Map.of()))
                .isEqualTo(CommandCatalog.resolve(defs, Map.of(), true));
    }
}

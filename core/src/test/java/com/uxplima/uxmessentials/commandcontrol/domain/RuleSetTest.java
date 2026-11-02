package com.uxplima.uxmessentials.commandcontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RuleSetTest {

    private static final String BYPASS = "test.bypass";

    private static final PlayerFacts PLAYER = new PlayerFacts() {
        @Override
        public Optional<String> group() {
            return Optional.empty();
        }

        @Override
        public boolean hasPermission(String node) {
            return false;
        }
    };

    private static RuleSet whitelist(String... entries) {
        return RuleSet.of(RuleMode.WHITELIST, List.of(entries), Map.of(), BYPASS);
    }

    private static RuleSet blacklist(String... entries) {
        return RuleSet.of(RuleMode.BLACKLIST, List.of(entries), Map.of(), BYPASS);
    }

    private static boolean runs(RuleSet rules, String command) {
        return rules.explain(command, PLAYER, true).allowed();
    }

    @Test
    void aBareCommandCoversEveryArgument() {
        RuleSet rules = whitelist("spawn");

        assertThat(runs(rules, "/spawn")).isTrue();
        assertThat(runs(rules, "/Spawn nether")).isTrue();
        assertThat(runs(rules, "/home")).isFalse();
    }

    @Test
    void anEntryWithArgumentsCoversOnlyThoseArguments() {
        RuleSet rules = whitelist("gamemode creative", "gamemode survival");

        assertThat(runs(rules, "/gamemode creative")).isTrue();
        assertThat(runs(rules, "/gamemode creative Steve")).isTrue();
        assertThat(runs(rules, "/gamemode spectator")).isFalse();
        assertThat(runs(rules, "/gamemode")).isFalse();
    }

    @Test
    void anExceptionTakesAnArgumentBackOut() {
        RuleSet allowed = whitelist("gamemode", "!gamemode spectator");
        RuleSet blocked = blacklist("gamemode", "!gamemode survival");

        assertThat(runs(allowed, "/gamemode creative")).isTrue();
        assertThat(runs(allowed, "/gamemode spectator")).isFalse();
        assertThat(runs(blocked, "/gamemode creative")).isFalse();
        assertThat(runs(blocked, "/gamemode survival")).isTrue();
    }

    @Test
    void anAsteriskStandsForAnyOneArgument() {
        RuleSet rules = blacklist("warp * set");

        assertThat(runs(rules, "/warp shop set")).isFalse();
        assertThat(runs(rules, "/warp shop")).isTrue();
    }

    @Test
    void aQuotedArgumentMeetsTheSameEntries() {
        RuleSet rules = blacklist("warp shop set");

        assertThat(runs(rules, "/warp \"shop\" set")).isFalse();
        assertThat(runs(rules, "/warp 'shop' set")).isFalse();
        assertThat(runs(rules, "/warp \"sh\\op\" set")).isFalse();
    }

    @Test
    void aCommandStaysVisibleWhileSomethingAfterItMayRun() {
        RuleSet rules = whitelist("gamemode creative");

        assertThat(rules.visible("gamemode", PLAYER, true)).isTrue();
        assertThat(rules.visible("gamemode creative", PLAYER, true)).isTrue();
        assertThat(rules.visible("gamemode spectator", PLAYER, true)).isFalse();
        assertThat(rules.visible("home", PLAYER, true)).isFalse();
    }

    @Test
    void aBlacklistExceptionKeepsItsCommandVisible() {
        RuleSet rules = blacklist("gamemode", "!gamemode survival");

        assertThat(rules.visible("gamemode", PLAYER, true)).isTrue();
        assertThat(rules.visible("gamemode creative", PLAYER, true)).isFalse();
    }

    @Test
    void aNamespaceDoesNotDodgeAnArgumentRule() {
        RuleSet rules = blacklist("gamemode creative");

        assertThat(runs(rules, "/minecraft:gamemode creative")).isFalse();
        assertThat(runs(rules, "/minecraft:gamemode survival")).isTrue();
        assertThat(rules.explain("/minecraft:gamemode creative", PLAYER, false).allowed())
                .as("without the fold the namespaced form is its own command")
                .isTrue();
    }

    @Test
    void theBypassRunsAndSeesEverything() {
        RuleSet rules = whitelist();
        PlayerFacts bypass = new PlayerFacts() {
            @Override
            public Optional<String> group() {
                return Optional.empty();
            }

            @Override
            public boolean hasPermission(String node) {
                return node.equals(BYPASS);
            }
        };

        assertThat(rules.explain("/stop", bypass, true).allowed()).isTrue();
        assertThat(rules.visible("stop now", bypass, true)).isTrue();
    }
}

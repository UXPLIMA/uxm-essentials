package com.uxplima.uxmessentials.commandcontrol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleMode;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the command-control typed config view: the safe shipped defaults (enabled, blacklist mode, the vanilla-style
 * deny line), that {@code commands.default} plus the per-group keys are read into the rule set, and that the
 * {@code default} key is treated as the fallback list rather than a group named "default".
 */
class CommandControlConfigTest {

    private static final String BYPASS = "uxmessentials.commandcontrol.bypass";
    private static final String VIEW = "uxmessentials.commandcontrol.viewplugins";

    @Test
    void defaultsAreSafeAndInert() {
        CommandControlConfig config = CommandControlConfig.from(new FixedConfig(Map.of(), Map.of(), List.of()));

        assertThat(config.enabled()).isTrue();
        assertThat(config.mode()).isEqualTo(RuleMode.BLACKLIST);
        assertThat(config.useUnknownCommandMessage()).isTrue();
        assertThat(config.denyMessage()).isEqualTo(CommandControlMessageKey.COMMANDCONTROL_UNKNOWN_COMMAND);
        assertThat(config.toRuleSet(BYPASS).isInert()).isTrue();

        // Tab-completion ships on (a no-op while the lists are empty); the plugin-hide ships off with the standard
        // plugin-listing list pre-filled, so it is one toggle away without leaving the module doing anything yet.
        assertThat(config.tabCompletionEnabled()).isTrue();
        assertThat(config.pluginHideEnabled()).isFalse();
        assertThat(config.denyListCommands()).isFalse();
        assertThat(config.hiddenCommands()).contains("plugins", "pl", "help", "icanhasbukkit");
        assertThat(config.toHidePolicy(VIEW).isActive()).isFalse();
    }

    @Test
    void pluginHideAndTabCompletionOverridesAreReadBack() {
        CommandControlConfig config = CommandControlConfig.from(new FixedConfig(
                Map.of(),
                Map.of(
                        "tab-completion.enabled", false,
                        "plugin-hide.enabled", true,
                        "plugin-hide.deny-list-commands", true),
                List.of(),
                Map.of("plugin-hide.hidden-commands", List.of("plugins", "pl"))));

        assertThat(config.tabCompletionEnabled()).isFalse();
        assertThat(config.pluginHideEnabled()).isTrue();
        assertThat(config.denyListCommands()).isTrue();
        assertThat(config.hiddenCommands()).containsExactly("plugins", "pl");
        assertThat(config.toHidePolicy(VIEW).isActive()).isTrue();
        assertThat(config.toHidePolicy(VIEW).isHiddenCommand("bukkit:plugins")).isTrue();
    }

    @Test
    void modeAndDenyMessageOverridesAreReadBack() {
        CommandControlConfig config = CommandControlConfig.from(
                new FixedConfig(Map.of("mode", "whitelist"), Map.of("use-unknown-command-message", false), List.of()));

        assertThat(config.mode()).isEqualTo(RuleMode.WHITELIST);
        assertThat(config.denyMessage()).isEqualTo(CommandControlMessageKey.COMMANDCONTROL_NO_PERMISSION);
    }

    @Test
    void defaultListAndGroupListsFeedTheRuleSet() {
        CommandControlConfig config = CommandControlConfig.from(new FixedConfig(
                Map.of("mode", "whitelist"),
                Map.of(),
                List.of("default", "vip"),
                Map.of(
                        "commands.default", List.of("spawn"),
                        "commands.vip", List.of("fly"))));

        // "default" is the fallback list, not a group named default.
        assertThat(config.defaultCommands()).containsExactly("spawn");
        assertThat(config.groupCommands()).containsOnlyKeys("vip");

        RuleSet rules = config.toRuleSet(BYPASS);
        assertThat(rules.decide("fly", facts("vip"))).isEqualTo(RuleSet.Decision.ALLOW);
        assertThat(rules.decide("spawn", facts("member"))).isEqualTo(RuleSet.Decision.ALLOW);
        assertThat(rules.decide("fly", facts("member"))).isEqualTo(RuleSet.Decision.DENY);
    }

    private static PlayerFacts facts(String group) {
        return new PlayerFacts() {
            @Override
            public Optional<String> group() {
                return Optional.of(group);
            }

            @Override
            public boolean hasPermission(String node) {
                return false;
            }
        };
    }

    /** A map-backed {@link ConfigStore} addressing keys by their dotted path relative to the module root. */
    private record FixedConfig(
            Map<String, String> strings,
            Map<String, Boolean> booleans,
            List<String> commandKeys,
            Map<String, List<String>> lists)
            implements ConfigStore {

        FixedConfig(Map<String, String> strings, Map<String, Boolean> booleans, List<String> commandKeys) {
            this(strings, booleans, commandKeys, Map.of());
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return booleans.getOrDefault(path, fallback);
        }

        @Override
        public String getString(String path, String fallback) {
            return strings.getOrDefault(path, fallback);
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return lists.getOrDefault(path, fallback);
        }

        @Override
        public List<String> getKeys(String path) {
            return path.equals("commands") ? commandKeys : List.of();
        }
    }
}

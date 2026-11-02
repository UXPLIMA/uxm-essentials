package com.uxplima.uxmessentials.commandcontrol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PluginEntriesTest {

    private static final Map<String, List<String>> INSTALLED =
            Map.of("uxmcosmetics", List.of("cosmetics", "kozmetik", "uxmcosmetics:cosmetics"));

    private static Collection<String> commands(String plugin) {
        return INSTALLED.getOrDefault(plugin.toLowerCase(Locale.ROOT), List.of());
    }

    @Test
    void aPluginEntryOpensIntoEveryCommandOfThePlugin() {
        assertThat(PluginEntries.expand(List.of("spawn", "plugin:uxmCosmetics"), PluginEntriesTest::commands))
                .containsExactly("spawn", "/cosmetics", "/kozmetik", "/uxmcosmetics:cosmetics");
    }

    @Test
    void anExceptionTakesThePluginsCommandsBackOut() {
        assertThat(PluginEntries.expand(List.of("!plugin:uxmcosmetics"), PluginEntriesTest::commands))
                .containsExactly("!/cosmetics", "!/kozmetik", "!/uxmcosmetics:cosmetics");
    }

    @Test
    void aPluginThatIsNotInstalledOpensIntoNothing() {
        assertThat(PluginEntries.expand(List.of("plugin:Missing", "plugin:", "home"), PluginEntriesTest::commands))
                .containsExactly("home");
    }
}

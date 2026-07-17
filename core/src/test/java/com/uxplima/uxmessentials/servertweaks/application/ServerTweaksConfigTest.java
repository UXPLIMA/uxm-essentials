package com.uxplima.uxmessentials.servertweaks.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link ServerTweaksConfig}'s resolution from the module's scoped config: an empty store leaves both tweaks off
 * (the whole module is inert until an operator opts in), the F3 brand falls back to the shipped default string, and
 * explicit keys switch each tweak on with its own values. The console-filter block also hands out a ready
 * {@code ConsoleFilterPolicy} that reflects the same switch and patterns.
 */
class ServerTweaksConfigTest {

    @Test
    void anEmptyStoreLeavesEveryTweakOff() {
        ServerTweaksConfig config = ServerTweaksConfig.from(new FixedConfig(Map.of()));

        assertThat(config.f3Brand().enabled()).isFalse();
        assertThat(config.f3Brand().brand()).isEqualTo("uxmEssentials");
        assertThat(config.consoleFilter().enabled()).isFalse();
        assertThat(config.consoleFilter().patterns()).isEmpty();
        // The derived policy suppresses nothing while the tweak is off.
        assertThat(config.consoleFilter().toPolicy().shouldSuppress("Can't keep up!"))
                .isFalse();
        assertThat(config.noChatReports().enabled()).isFalse();
        // With the tweak off, a signed message is never re-delivered unsigned.
        assertThat(config.noChatReports().toPolicy().shouldDeliverUnsigned(false))
                .isFalse();
        assertThat(config.signedVelocity().enabled()).isFalse();
    }

    @Test
    void explicitKeysTurnEachTweakOnWithItsValues() {
        ServerTweaksConfig config = ServerTweaksConfig.from(new FixedConfig(Map.ofEntries(
                Map.entry("f3-brand.enabled", true),
                Map.entry("f3-brand.brand", "MyNetwork"),
                Map.entry("console-filter.enabled", true),
                Map.entry("console-filter.patterns", List.of("Can't keep up!", "moving too quickly")),
                Map.entry("no-chat-reports.enabled", true),
                Map.entry("signed-velocity.enabled", true))));

        assertThat(config.f3Brand().enabled()).isTrue();
        assertThat(config.f3Brand().brand()).isEqualTo("MyNetwork");
        assertThat(config.consoleFilter().enabled()).isTrue();
        assertThat(config.consoleFilter().patterns()).containsExactly("Can't keep up!", "moving too quickly");
        assertThat(config.consoleFilter().toPolicy().shouldSuppress("[WARN]: Can't keep up! overloaded?"))
                .isTrue();
        assertThat(config.noChatReports().enabled()).isTrue();
        // Enabled: a signed message is re-delivered unsigned, an already-unsigned one is left alone.
        assertThat(config.noChatReports().toPolicy().shouldDeliverUnsigned(false))
                .isTrue();
        assertThat(config.noChatReports().toPolicy().shouldDeliverUnsigned(true))
                .isFalse();
        assertThat(config.signedVelocity().enabled()).isTrue();
    }

    /** A map-backed {@link ConfigStore} that honours the boolean/string/list getters the config reads. */
    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }

        @Override
        @SuppressWarnings("unchecked") // the test only ever stores List<String> at a list path
        public List<String> getStringList(String path, List<String> fallback) {
            return values.get(path) instanceof List<?> list ? List.copyOf((List<String>) list) : fallback;
        }
    }
}

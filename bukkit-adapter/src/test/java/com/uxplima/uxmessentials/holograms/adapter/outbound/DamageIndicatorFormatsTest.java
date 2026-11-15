package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/** The damage numbers are drawn in theme colours unless an operator wrote formats of their own. */
class DamageIndicatorFormatsTest {

    private final Notices log = new Notices();

    /** A server start that has said nothing yet. */
    private final AtomicBoolean told = new AtomicBoolean();

    @Test
    void unwrittenFormatsUseTheThemeColours() {
        DamageIndicatorConfig config = DamageIndicatorConfig.fromConfig(new Written(), log, told);

        assertThat(config.format(4.0, DamageIndicatorConfig.Kind.DAMAGE)).isEqualTo("<bad>-4");
        assertThat(config.format(6.5, DamageIndicatorConfig.Kind.CRIT)).isEqualTo("<warn><bold>-6.5 ✦");
        assertThat(config.format(2.0, DamageIndicatorConfig.Kind.HEAL)).isEqualTo("<good>+2");
        assertThat(log.notices).isEmpty();
    }

    @Test
    void theNamedColoursTheFileShippedWithFollowTheThemeToo() {
        DamageIndicatorConfig config = DamageIndicatorConfig.fromConfig(shippedNamedColours(true), log, told);

        assertThat(config.format(4.0, DamageIndicatorConfig.Kind.DAMAGE)).isEqualTo("<bad>-4");
        assertThat(config.format(4.0, DamageIndicatorConfig.Kind.CRIT)).isEqualTo("<warn><bold>-4 ✦");
        assertThat(config.format(4.0, DamageIndicatorConfig.Kind.HEAL)).isEqualTo("<good>+4");
        assertThat(log.notices).hasSize(1);
    }

    @Test
    void aFormatTheOperatorWroteIsKept() {
        DamageIndicatorConfig config = DamageIndicatorConfig.fromConfig(
                new Written()
                        .set("damage-indicators.enabled", true)
                        .set("damage-indicators.damage-format", "<dark_red>{amount} dmg"),
                log,
                told);

        assertThat(config.format(3.0, DamageIndicatorConfig.Kind.DAMAGE)).isEqualTo("<dark_red>3 dmg");
        assertThat(log.notices).isEmpty();
    }

    @Test
    void theShippedColoursAreMentionedOncePerServerStart() {
        DamageIndicatorConfig.fromConfig(shippedNamedColours(true), log, told);
        DamageIndicatorConfig.fromConfig(shippedNamedColours(true), log, told);

        assertThat(log.notices).hasSize(1);
    }

    @Test
    void theShippedColoursAreNotMentionedWhileTheIndicatorsAreOff() {
        DamageIndicatorConfig config = DamageIndicatorConfig.fromConfig(shippedNamedColours(false), log, told);

        assertThat(config.format(4.0, DamageIndicatorConfig.Kind.DAMAGE)).isEqualTo("<bad>-4");
        assertThat(log.notices).isEmpty();
    }

    /** A file that still holds the named-colour formats it shipped with. */
    private static Written shippedNamedColours(boolean enabled) {
        return new Written()
                .set("damage-indicators.enabled", enabled)
                .set("damage-indicators.damage-format", "<red>-{amount}")
                .set("damage-indicators.crit-format", "<gold><bold>-{amount} ✦")
                .set("damage-indicators.heal-format", "<green>+{amount}");
    }

    /** Keeps the info lines, the ones an operator is told about a value read differently. */
    private static final class Notices implements Logger {

        final List<String> notices = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            notices.add(message);
        }

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** The holograms config as an operator wrote it: the keys given here, every other key at its default. */
    private static final class Written implements ConfigStore {

        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Boolean> switches = new HashMap<>();

        Written set(String path, String value) {
            values.put(path, value);
            return this;
        }

        Written set(String path, boolean value) {
            switches.put(path, value);
            return this;
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return switches.getOrDefault(path, fallback);
        }

        @Override
        public String getString(String path, String fallback) {
            return values.getOrDefault(path, fallback);
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}

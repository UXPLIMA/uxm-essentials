package com.uxplima.uxmessentials.customcommands.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link CustomCommandsModule} feature-module contract and the settings {@link CustomCommandsConfig} reads:
 * the id and config root, the default-on gate with its opt-out, the single {@code /customcmd} descriptor, and the
 * empty listener and migration lists (the context persists nothing and installs its one listener from the wiring).
 * The registry position is covered by {@code FeatureModuleRegistryDriftTest}.
 */
class CustomCommandsModuleTest {

    @Test
    void reportsItsIdAndConfigRoot() {
        CustomCommandsModule module = new CustomCommandsModule();

        assertThat(module.id()).isEqualTo(ModuleId.of("customcommands"));
        assertThat(module.configRoot()).isEqualTo("modules.customcommands");
    }

    @Test
    void shipsEnabledByDefaultAndHonoursAnExplicitOptOut() {
        CustomCommandsModule module = new CustomCommandsModule();

        assertThat(module.enabled(new FixedConfig(Map.of()))).isTrue();
        assertThat(module.enabled(new FixedConfig(Map.of("modules.customcommands.enabled", false))))
                .isFalse();
    }

    @Test
    void publishesTheAdminCommandAndNothingElse() {
        CustomCommandsModule module = new CustomCommandsModule();

        assertThat(module.commands()).singleElement().satisfies(spec -> {
            assertThat(spec.literal()).isEqualTo("customcmd");
            assertThat(spec.permission()).isEqualTo("uxmessentials.customcommand.admin");
        });
        assertThat(module.listeners()).isEmpty();
        assertThat(module.migrations()).isEmpty();
    }

    @Test
    void startAndStopTrackTheRunningFlag() {
        CustomCommandsModule module = new CustomCommandsModule();
        assertThat(module.isRunning()).isFalse();

        module.stop();
        assertThat(module.isRunning()).isFalse();
    }

    @Test
    void theConfigFallsBackToTheShippedPolicyWhenTheBlockIsEmpty() {
        CustomCommandsConfig config = CustomCommandsConfig.from(new FixedConfig(Map.of()));

        assertThat(config.enabled()).isTrue();
        assertThat(config.allowConsoleActions()).isTrue();
        assertThat(config.allowOpActions()).isFalse();
        assertThat(config.maxChainDepth()).isEqualTo(5);
        assertThat(config.maxDelay()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.maxDelayedSteps()).isEqualTo(20);
        assertThat(config.logPrivilegedActions()).isTrue();
        assertThat(config.currency()).isEqualTo("vault");
    }

    @Test
    void theConfigReadsTheOperatorsOwnValuesAndClampsTheNonsensicalOnes() {
        CustomCommandsConfig config = CustomCommandsConfig.from(new FixedConfig(Map.of(
                "allow-console-actions",
                false,
                "allow-op-actions",
                true,
                "max-chain-depth",
                -3,
                "max-delay",
                "5s",
                "max-delayed-steps",
                -1,
                "default-currency",
                "exp")));

        assertThat(config.allowConsoleActions()).isFalse();
        assertThat(config.allowOpActions()).isTrue();
        assertThat(config.maxChainDepth()).isEqualTo(1);
        assertThat(config.maxDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.maxDelayedSteps()).isZero();
        assertThat(config.currency()).isEqualTo("exp");
        assertThat(config.chainLimits().maxDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.chainLimits().maxDelayedSteps()).isZero();
    }

    @Test
    void anUnreadableDelayFallsBackToTheShippedCeilingRatherThanRefusingToLoad() {
        CustomCommandsConfig config = CustomCommandsConfig.from(new FixedConfig(Map.of("max-delay", "soon")));

        assertThat(config.maxDelay()).isEqualTo(Duration.ofSeconds(60));
    }

    /** A map-backed {@link ConfigStore} for driving the enable gate and the settings reader. */
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
    }
}

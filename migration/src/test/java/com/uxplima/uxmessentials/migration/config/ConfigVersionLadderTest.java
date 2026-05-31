package com.uxplima.uxmessentials.migration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.uxplima.uxmessentials.migration.config.step.V1__seed_migration_defaults;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * The config-version ladder guard (docs/12-migration §12.3). Asserts the four ladder properties: numbered
 * and contiguous, idempotent, tracked via the {@code config-version} key, and never destructive. A fresh
 * config ships at the target version and triggers zero steps; an old config upgrades to the target and a
 * second run is a no-op.
 */
class ConfigVersionLadderTest {

    private final ConfigVersionLadder ladder = new ConfigVersionLadder(List.of(new V1__seed_migration_defaults()));

    @Test
    void anUnversionedConfigUpgradesToTheTargetAndIsThenCurrent() {
        ConfigurationNode root = CommentedConfigurationNode.root();

        MigrationResult result = ladder.upgrade(root);

        assertThat(result.from()).isZero();
        assertThat(result.to()).isEqualTo(ladder.targetVersion());
        assertThat(result.upgraded()).isTrue();
        assertThat(root.node("config-version").getInt()).isEqualTo(ladder.targetVersion());
        assertThat(root.node("source-path").getString()).isEqualTo("Essentials");
        assertThat(root.node("on-conflict").getString()).isEqualTo("skip");
        assertThat(root.node("balance-policy").getString()).isEqualTo("skip-if-present");
    }

    @Test
    void reRunningTheLadderOnAnUpgradedConfigIsANoOp() {
        ConfigurationNode root = CommentedConfigurationNode.root();
        ladder.upgrade(root);

        MigrationResult second = ladder.upgrade(root);

        assertThat(second.upgraded()).isFalse();
        assertThat(second.from()).isEqualTo(ladder.targetVersion());
        assertThat(second.to()).isEqualTo(ladder.targetVersion());
    }

    @Test
    void anExistingValueIsPreservedNotOverwritten() {
        ConfigurationNode root = CommentedConfigurationNode.root();
        setRaw(root.node("on-conflict"), "overwrite");

        ladder.upgrade(root);

        // The step seeds only absent keys, so an operator's chosen value survives the upgrade.
        assertThat(root.node("on-conflict").getString()).isEqualTo("overwrite");
    }

    @Test
    void aFreshConfigAtTheTargetTriggersZeroSteps() {
        ConfigurationNode root = CommentedConfigurationNode.root();
        setVersion(root, ladder.targetVersion());

        MigrationResult result = ladder.upgrade(root);

        assertThat(result.upgraded()).isFalse();
    }

    @Test
    void nonContiguousStepsAreRejectedAtConstruction() {
        assertThatThrownBy(() -> new ConfigVersionLadder(List.of(stepAt(2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contiguous");
    }

    private static ConfigStep stepAt(int version) {
        return new ConfigStep() {
            @Override
            public int targetVersion() {
                return version;
            }

            @Override
            public void apply(ConfigurationNode root) {}
        };
    }

    private static void setRaw(ConfigurationNode node, String value) {
        try {
            node.set(value);
        } catch (org.spongepowered.configurate.serialize.SerializationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void setVersion(ConfigurationNode root, int version) {
        try {
            root.node("config-version").set(version);
        } catch (org.spongepowered.configurate.serialize.SerializationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}

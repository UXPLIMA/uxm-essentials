package com.uxplima.uxmessentials.migration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.uxmessentials.migration.config.step.V1__seed_migration_defaults;
import com.uxplima.uxmessentials.migration.config.step.V2__seed_litebans_source;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * The V2 config step seeds the {@code litebans} connection subtree, idempotently and non-destructively. An
 * unversioned config climbs the full ladder to version 2 with both blocks seeded; an operator's chosen value
 * survives a re-run.
 */
class V2SeedLiteBansSourceTest {

    private final ConfigVersionLadder ladder =
            new ConfigVersionLadder(List.of(new V1__seed_migration_defaults(), new V2__seed_litebans_source()));

    @Test
    void theLadderReachesVersionTwoAndSeedsTheLiteBansBlock() {
        ConfigurationNode root = CommentedConfigurationNode.root();

        MigrationResult result = ladder.upgrade(root);

        assertThat(result.to()).isEqualTo(2);
        assertThat(root.node("litebans", "jdbc-url").getString()).isEmpty();
        assertThat(root.node("litebans", "username").getString()).isEmpty();
        assertThat(root.node("litebans", "password").getString()).isEmpty();
        assertThat(root.node("litebans", "table-prefix").getString()).isEqualTo("litebans_");
    }

    @Test
    void anOperatorsConfiguredJdbcUrlSurvivesTheUpgrade() throws Exception {
        ConfigurationNode root = CommentedConfigurationNode.root();
        root.node("litebans", "jdbc-url").set("jdbc:mariadb://localhost/litebans");

        ladder.upgrade(root);

        assertThat(root.node("litebans", "jdbc-url").getString()).isEqualTo("jdbc:mariadb://localhost/litebans");
    }

    @Test
    void reRunningTheLadderIsANoOp() {
        ConfigurationNode root = CommentedConfigurationNode.root();
        ladder.upgrade(root);

        MigrationResult second = ladder.upgrade(root);

        assertThat(second.upgraded()).isFalse();
    }
}

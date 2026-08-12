package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /modstats} into the moderation context's command surface and its gating node. The staff
 * punishment-analytics command carries its own {@code uxmessentials.moderation.stats} node (analytics can be
 * granted apart from the act-on-sanction powers); this guard fails if the literal drops out of the surface or
 * wires under the wrong node, which would otherwise drift the permissions reference and the permission catalogue.
 */
class ModStatsSurfaceDriftTest {

    private static CommandSpec moderationSpec(String literal) {
        FeatureModule moderation = new DefaultModuleRegistry()
                .byId(ModuleId.of("moderation"))
                .orElseThrow(() -> new AssertionError("moderation module must be registered"));
        return moderation.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("moderation surface must expose a /" + literal + " command"));
    }

    @Test
    void moderationSurfaceExposesModStats() {
        assertThat(moderationSpec("modstats").literal()).isEqualTo("modstats");
    }

    @Test
    void modStatsWiresUnderItsOwnNode() {
        assertThat(moderationSpec("modstats").permission()).isEqualTo("uxmessentials.moderation.stats");
    }
}

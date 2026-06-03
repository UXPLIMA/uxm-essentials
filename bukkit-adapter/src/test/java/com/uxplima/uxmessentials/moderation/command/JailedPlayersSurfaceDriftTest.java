package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /jailedplayers} into the moderation context's command surface. Staff can list the configured
 * jails with {@code /jails}, but there was no way to review who is currently <em>held</em> in one; this guard
 * fails if the literal drops out of the surface or wires under a node other than the shared
 * {@code uxmessentials.moderation.jail}.
 */
class JailedPlayersSurfaceDriftTest {

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
    void moderationSurfaceExposesJailedPlayers() {
        assertThat(moderationSpec("jailedplayers").literal()).isEqualTo("jailedplayers");
    }

    @Test
    void jailedPlayersSharesTheJailNode() {
        assertThat(moderationSpec("jailedplayers").permission()).isEqualTo("uxmessentials.moderation.jail");
    }
}

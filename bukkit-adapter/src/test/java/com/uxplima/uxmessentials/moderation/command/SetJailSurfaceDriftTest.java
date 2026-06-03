package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /setjail} and {@code /deljail} into the moderation context's command surface. Jails were
 * config-only and read-only; these define and remove a DB-backed jail at the staff member's location. This
 * guard fails if either literal drops out of the surface or wires under a node other than the shared
 * {@code uxmessentials.moderation.jail}.
 */
class SetJailSurfaceDriftTest {

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
    void moderationSurfaceExposesSetJailAndDelJail() {
        assertThat(moderationSpec("setjail").literal()).isEqualTo("setjail");
        assertThat(moderationSpec("deljail").literal()).isEqualTo("deljail");
    }

    @Test
    void bothShareTheJailNode() {
        assertThat(moderationSpec("setjail").permission()).isEqualTo("uxmessentials.moderation.jail");
        assertThat(moderationSpec("deljail").permission()).isEqualTo("uxmessentials.moderation.jail");
    }
}

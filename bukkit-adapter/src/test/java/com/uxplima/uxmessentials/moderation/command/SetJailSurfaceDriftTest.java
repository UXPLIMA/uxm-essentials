package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /setjail} into the moderation context's command surface under the shared
 * {@code uxmessentials.moderation.jail} node, and pins that jail removal is no longer a standalone literal:
 * removing a jail folded into {@code /jail del} (mirroring {@code /warp del}), so the surface must not carry a
 * {@code deljail} command. This guard fails if {@code setjail} drops out or rewires, or if a {@code deljail}
 * literal reappears.
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
    void moderationSurfaceExposesSetJailUnderTheJailNode() {
        assertThat(moderationSpec("setjail").literal()).isEqualTo("setjail");
        assertThat(moderationSpec("setjail").permission()).isEqualTo("uxmessentials.moderation.jail");
    }

    @Test
    void jailRemovalIsFoldedIntoJailNotAStandaloneLiteral() {
        FeatureModule moderation = new DefaultModuleRegistry()
                .byId(ModuleId.of("moderation"))
                .orElseThrow(() -> new AssertionError("moderation module must be registered"));
        assertThat(moderation.commands().stream().map(CommandSpec::literal)).doesNotContain("deljail");
    }
}

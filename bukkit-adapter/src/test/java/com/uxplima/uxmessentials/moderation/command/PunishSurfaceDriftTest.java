package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /punish} into the moderation context's command surface and its gating node. The
 * punishment-template convenience carries its own {@code uxmessentials.moderation.templates} node (the preset
 * power can be granted apart from the raw ban/tempban nodes); this guard fails if the literal drops out of the
 * surface or wires under the wrong node, which would otherwise drift the permissions reference and
 * the permission catalogue.
 */
class PunishSurfaceDriftTest {

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
    void moderationSurfaceExposesPunish() {
        assertThat(moderationSpec("punish").literal()).isEqualTo("punish");
    }

    @Test
    void punishWiresUnderItsOwnTemplatesNode() {
        assertThat(moderationSpec("punish").permission()).isEqualTo("uxmessentials.moderation.templates");
    }
}

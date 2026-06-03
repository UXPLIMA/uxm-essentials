package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /banlist} into the moderation context's command surface. Staff need to review who is currently
 * banned; the verb is absent from the ban/unban/tempban/banip family until now. This guard fails if the
 * literal drops out of the surface or wires under a node other than {@code uxmessentials.moderation.banlist}.
 */
class BanListSurfaceDriftTest {

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
    void moderationSurfaceExposesBanList() {
        assertThat(moderationSpec("banlist").literal()).isEqualTo("banlist");
    }

    @Test
    void banListWiresUnderItsOwnNode() {
        assertThat(moderationSpec("banlist").permission()).isEqualTo("uxmessentials.moderation.banlist");
    }
}

package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /banhistory} and {@code /mutehistory} into the moderation context's command surface. Staff need
 * to review a player's full disciplinary record (not just the sanctions still in effect); the verbs reuse the
 * existing ban and mute nodes rather than minting new ones. This guard fails if either literal drops out of
 * the surface or wires under a node other than its respective family node.
 */
class BanHistorySurfaceDriftTest {

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
    void moderationSurfaceExposesBanHistory() {
        assertThat(moderationSpec("banhistory").literal()).isEqualTo("banhistory");
    }

    @Test
    void banHistoryWiresUnderTheBanNode() {
        assertThat(moderationSpec("banhistory").permission()).isEqualTo("uxmessentials.moderation.ban");
    }

    @Test
    void moderationSurfaceExposesMuteHistory() {
        assertThat(moderationSpec("mutehistory").literal()).isEqualTo("mutehistory");
    }

    @Test
    void muteHistoryWiresUnderTheMuteNode() {
        assertThat(moderationSpec("mutehistory").permission()).isEqualTo("uxmessentials.moderation.mute");
    }
}

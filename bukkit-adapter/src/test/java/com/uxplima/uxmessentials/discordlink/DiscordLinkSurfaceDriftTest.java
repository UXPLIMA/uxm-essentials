package com.uxplima.uxmessentials.discordlink;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /discordlink} and {@code /discordunlink} into the discordlink context's command surface and the
 * single self-service node both wire under. The guard fails if either literal drops out of the surface or wires
 * under a node other than the shared {@code uxmessentials.discord.link}.
 */
class DiscordLinkSurfaceDriftTest {

    private static CommandSpec discordlinkSpec(String literal) {
        FeatureModule discordlink = new DefaultModuleRegistry()
                .byId(ModuleId.of("discordlink"))
                .orElseThrow(() -> new AssertionError("discordlink module must be registered"));
        return discordlink.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("discordlink surface must expose a /" + literal + " command"));
    }

    @Test
    void surfaceExposesLinkAndUnlink() {
        assertThat(discordlinkSpec("discordlink").literal()).isEqualTo("discordlink");
        assertThat(discordlinkSpec("discordunlink").literal()).isEqualTo("discordunlink");
    }

    @Test
    void bothShareTheDiscordLinkNode() {
        assertThat(discordlinkSpec("discordlink").permission()).isEqualTo("uxmessentials.discord.link");
        assertThat(discordlinkSpec("discordunlink").permission()).isEqualTo("uxmessentials.discord.link");
    }
}

package com.uxplima.uxmessentials.messaging.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /rtoggle} into the messaging context's command surface. It is the reply-routing companion of
 * {@code /msgtoggle} and, like it, wires under {@code uxmessentials.msg.toggle}. This guard fails if the
 * literal drops out of the surface or wires under a different node.
 */
class RtoggleSurfaceDriftTest {

    private static CommandSpec messagingSpec(String literal) {
        FeatureModule messaging = new DefaultModuleRegistry()
                .byId(ModuleId.of("messaging"))
                .orElseThrow(() -> new AssertionError("messaging module must be registered"));
        return messaging.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("messaging surface must expose a /" + literal + " command"));
    }

    @Test
    void messagingSurfaceExposesRtoggle() {
        assertThat(messagingSpec("rtoggle").literal()).isEqualTo("rtoggle");
    }

    @Test
    void rtoggleSharesTheMsgToggleNode() {
        assertThat(messagingSpec("rtoggle").permission()).isEqualTo("uxmessentials.msg.toggle");
    }
}

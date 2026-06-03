package com.uxplima.uxmessentials.communication.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /clearchat} into the communication context's command surface. The chat-family staff verb flushes
 * the visible chat by sending a screenful of blank lines to online players; zEssentials ships
 * {@code CommandChatClear}. This guard fails if the literal drops out of the surface or wires under a node
 * other than {@code uxmessentials.communication.clearchat}.
 */
class ClearChatSurfaceDriftTest {

    private static CommandSpec communicationSpec(String literal) {
        FeatureModule communication = new DefaultModuleRegistry()
                .byId(ModuleId.of("communication"))
                .orElseThrow(() -> new AssertionError("communication module must be registered"));
        return communication.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("communication surface must expose a /" + literal + " command"));
    }

    @Test
    void communicationSurfaceExposesClearChat() {
        assertThat(communicationSpec("clearchat").literal()).isEqualTo("clearchat");
    }

    @Test
    void clearChatWiresUnderItsOwnPermission() {
        assertThat(communicationSpec("clearchat").permission()).isEqualTo("uxmessentials.communication.clearchat");
    }
}

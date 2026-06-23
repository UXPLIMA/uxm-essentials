package com.uxplima.uxmessentials.worlds.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.GuiRootBinding;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.adapter.WorldsServices;
import com.uxplima.uxmessentials.worlds.adapter.inbound.command.WorldCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Bare {@code /worlds} GUI wiring: the command exposes its world-list opener through {@code guiRoot()}, so with the
 * catalog {@code gui} flag on the shared {@link GuiRootBinding} installs that opener as the bare-root executor while
 * every subcommand child carries across; with gui off the root is left bare for the usage fallback. MockBukkit boots
 * Paper's Brigadier so the node rebuild is wired.
 */
class WorldGuiRootTest {

    private WorldCommand command;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        command = new WorldCommand(mock(WorldsServices.class), new KeyMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void exposesAGuiRootOpener() {
        assertThat(command.guiRoot()).isPresent();
    }

    @Test
    void bareWorldsInstallsTheListOpenerWhenGuiOn() {
        LiteralCommandNode<CommandSourceStack> node =
                binding(true).wrap(command).build();

        assertThat(node.getLiteral()).isEqualTo("worlds");
        // The bare root carries an executor (the GUI opener) and the existing subcommands stay.
        assertThat(node.getCommand()).isNotNull();
        assertThat(node.getChild("create")).isNotNull();
        assertThat(node.getChild("gui")).isNotNull();
        assertThat(node.getChild("delete")).isNotNull();
    }

    @Test
    void bareWorldsFallsBackToUsageWhenGuiOff() {
        LiteralCommandNode<CommandSourceStack> node =
                binding(false).wrap(command).build();

        // gui off leaves the root bare so the usage binding can later inject its usage executor.
        assertThat(node.getCommand()).isNull();
        assertThat(node.getChild("create")).isNotNull();
    }

    private static GuiRootBinding binding(boolean gui) {
        return new GuiRootBinding(
                Map.of("worlds", new EffectiveCommand(new CommandId("worlds"), "worlds", List.of(), true, gui)));
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}

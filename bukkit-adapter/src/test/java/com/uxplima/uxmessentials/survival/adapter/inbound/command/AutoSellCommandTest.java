package com.uxplima.uxmessentials.survival.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /autosell}. Proves the command node builds and registers under the {@code autosell}
 * literal (the node the survival wiring now registers because the mechanic ships enabled) and that, because the
 * per-player toggle now defaults off, a first dispatch opts the player in rather than being a silent no-op.
 */
class AutoSellCommandTest {

    private ServerMock server;
    private PdcSurvivalToggles toggles;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        toggles = new PdcSurvivalToggles();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theCommandNodeBuildsUnderTheAutosellLiteral() {
        LiteralCommandNode<CommandSourceStack> node = command().build();

        assertThat(node.getLiteral()).isEqualTo("autosell");
    }

    @Test
    void aFirstDispatchOptsThePlayerInFromTheOffDefault() {
        PlayerMock player = server.addPlayer("Steve");
        player.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.survival.autosell.toggle", true);
        // The per-player toggle defaults off, so nobody auto-sells until they run the command.
        assertThat(toggles.autoSellActive(player, false)).isFalse();

        dispatch(player, "autosell");

        assertThat(toggles.autoSellActive(player, false)).isTrue();
    }

    private AutoSellCommand command() {
        return new AutoSellCommand(toggles, new NoOpMessages());
    }

    private void dispatch(PlayerMock sender, String input) {
        LiteralCommandNode<CommandSourceStack> node = command().build();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (CommandSyntaxException blockedOrBadSyntax) {
            // A blocked node or a usage miss surfaces here; the assertions cover the observable effect.
        }
    }

    /** A Messages stub that renders every key to an empty string, so the command's feedback path stays inert. */
    private static final class NoOpMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return "";
        }
    }
}

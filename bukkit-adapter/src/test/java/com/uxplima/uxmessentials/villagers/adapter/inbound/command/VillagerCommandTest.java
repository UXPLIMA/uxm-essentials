package com.uxplima.uxmessentials.villagers.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Pins the /villager root's shape when the villagers module is on but every sub-feature is off (the shipped default):
 * the command still registers (built with no subcommands), its root is gated on {@code uxmessentials.villagers.use}
 * so it is hidden from a player without the node, and its bare executor reports that no villager tools are enabled -
 * rather than the command silently not existing while the module reads as running.
 */
class VillagerCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String USE = "uxmessentials.villagers.use";

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theRootRegistersEvenWithEverySubFeatureOff() {
        assertThat(allFeaturesOff().build().getLiteral()).isEqualTo("villager");
    }

    @Test
    void theRootIsGatedOnTheVillagersUseNode() {
        LiteralCommandNode<CommandSourceStack> node = allFeaturesOff().build();
        PlayerMock plain = server.addPlayer("Plain");
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), USE, true);

        assertThat(node.getRequirement().test(CommandSourceStackMock.from(plain)))
                .isFalse();
        assertThat(node.getRequirement().test(CommandSourceStackMock.from(staff)))
                .isTrue();
    }

    @Test
    void theBareRootReportsNoToolsWhenEverySubFeatureIsOff() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), USE, true);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(allFeaturesOff().build());

        try {
            dispatcher.execute("villager", CommandSourceStackMock.from(staff));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: villager", e);
        }

        assertThat(PLAIN.serialize(staff.nextComponentMessage())).contains("villagers.none-enabled");
    }

    /** The command exactly as {@code VillagersWiring} builds it when the module is on but no sub-feature is switched on. */
    private VillagerCommand allFeaturesOff() {
        return new VillagerCommand(null, null, null, null, null, null, new KeyMessages());
    }

    /** Resolves any key to its dotted catalog id (and the prefix to empty) so the rendered line is observable. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key().equals("prefix") ? "" : key.key();
        }
    }
}

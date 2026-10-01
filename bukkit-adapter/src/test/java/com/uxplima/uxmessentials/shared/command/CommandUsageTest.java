package com.uxplima.uxmessentials.shared.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandUsage;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * A usage line a command writes for itself describes the command the way the catalogue does.
 *
 * <p>Eight modules wrote their own usage lines with an English description in the code. A Turkish player who typed
 * {@code /pay} read "Pay money to a player", although the catalogue says "Başka bir oyuncuya para gönder.", and the
 * line missed the theme's lettering every catalogue line carries.
 */
class CommandUsageTest {

    private ServerMock server;
    private final Recording messages = new Recording();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the description is the catalogue's line for the command, in the reader's language")
    void theDescriptionComesFromTheCatalogue() {
        messages.answers.put("describe.pay", "Başka bir oyuncuya para gönder.");

        CommandUsage.send(messages, server.addPlayer("Ayse"), "pay", "<player> <amount> [currency]", "Pay money");

        assertThat(messages.placeholders)
                .containsEntry("command", "pay")
                .containsEntry("usage", "<player> <amount> [currency]")
                .containsEntry("description", "Başka bir oyuncuya para gönder.");
    }

    @Test
    @DisplayName("a nested command is described by its root, and the code's English stands in for a missing line")
    void aNestedCommandIsDescribedByItsRoot() {
        messages.answers.put("describe.hologram", "Manage holograms.");

        CommandUsage.send(messages, server.getConsoleSender(), "hologram create", "<name>", "Create a hologram");
        assertThat(messages.placeholders).containsEntry("description", "Manage holograms.");

        CommandUsage.send(messages, server.getConsoleSender(), "npc", "<id>", "Manage NPCs");
        assertThat(messages.placeholders).containsEntry("description", "Manage NPCs");
    }

    @Test
    @DisplayName("an argument named like a colour of the theme stays in the line")
    void anArgumentNamedLikeAColourStays() {
        CommandUsage.send(messages, server.getConsoleSender(), "itemmodel", "(clear|<value>)", "Set a model");

        assertThat(messages.placeholders.get("usage"))
                .isNotEqualTo("(clear|<value>)")
                .contains("value");
    }

    private static final class Recording implements Messages {
        private final Map<String, String> answers = new HashMap<>();
        private Map<String, String> placeholders = Map.of();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> values) {
            String answer = answers.get(key.key());
            if (answer != null) {
                return answer;
            }
            if (key == SharedMessageKey.COMMAND_USAGE) {
                placeholders = Map.copyOf(values);
            }
            return key.key();
        }
    }
}

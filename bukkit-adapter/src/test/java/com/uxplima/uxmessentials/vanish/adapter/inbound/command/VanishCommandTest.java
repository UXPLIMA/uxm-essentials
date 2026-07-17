package com.uxplima.uxmessentials.vanish.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishLevelResolver;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishView;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.application.ListVanished;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import com.uxplima.uxmessentials.vanish.application.VanishNotifier;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /vanish <player>} and {@code /vanish list}: dispatching {@code /vanish <player>} flips
 * another player's vanish and confirms to the actor; {@code /vanish list} is gated by {@code uxmessentials.vanish.list}
 * and {@code /vanish <player>} by {@code uxmessentials.vanish.others}; the list shows the hidden players the caller may
 * see. A synchronous inline scheduler runs the view's entity hop deterministically.
 */
class VanishCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private InMemoryVanishStore store;
    private VanishCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        InlineScheduler scheduler = new InlineScheduler();
        store = new InMemoryVanishStore();
        BukkitVanishLevelResolver levels = new BukkitVanishLevelResolver();
        BukkitVanishView view = new BukkitVanishView(MockBukkit.createMockPlugin(), scheduler, levels);
        ToggleVanish toggleVanish =
                new ToggleVanish(store, view, levels, new VanishNotifier(new KeyMessages(), new DiscardingSink()));
        ListVanished listVanished = new ListVanished(store, levels);
        command = new VanishCommand(toggleVanish, listVanished, server, new KeyMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void vanishOtherTogglesAnotherPlayerAndConfirmsToTheActor() {
        PlayerMock bob = server.addPlayer("Bob");
        PlayerMock actor = server.addPlayer("Staff");
        actor.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.use", true);
        actor.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.others", true);

        execute(CommandSourceStackMock.from(actor), "vanish Bob");

        assertThat(store.isVanished(bob.getUniqueId())).isTrue();
        assertThat(lastMessage(actor)).contains("vanish.other-on").contains("player=Bob");
    }

    @Test
    void theListNodeIsGatedByTheListPermission() {
        PlayerMock permitted = server.addPlayer("Permitted");
        permitted.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.list", true);
        PlayerMock denied = server.addPlayer("Denied");

        var listNode = command.build().getChild("list");
        assertThat(listNode.getRequirement().test(CommandSourceStackMock.from(permitted)))
                .isTrue();
        assertThat(listNode.getRequirement().test(CommandSourceStackMock.from(denied)))
                .isFalse();
    }

    @Test
    void theOthersNodeIsGatedByTheOthersPermission() {
        PlayerMock permitted = server.addPlayer("Permitted");
        permitted.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.others", true);
        PlayerMock denied = server.addPlayer("Denied");

        var othersNode = command.build().getChild("target");
        assertThat(othersNode.getRequirement().test(CommandSourceStackMock.from(permitted)))
                .isTrue();
        assertThat(othersNode.getRequirement().test(CommandSourceStackMock.from(denied)))
                .isFalse();
    }

    @Test
    void listShowsTheVanishedPlayersTheCallerCanSee() {
        PlayerMock bob = server.addPlayer("Bob");
        store.vanish(bob.getUniqueId(), VanishLevel.DEFAULT);
        PlayerMock caller = server.addPlayer("Caller");
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.use", true);
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.list", true);
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.see", true); // see level 1

        execute(CommandSourceStackMock.from(caller), "vanish list");

        assertThat(lastMessage(caller)).contains("vanish.list").contains("Bob").contains("count=1");
    }

    @Test
    void listReportsNobodyWhenTheCallerSeesNoVanishedPlayer() {
        PlayerMock caller = server.addPlayer("Caller");
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.use", true);
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.list", true);

        execute(CommandSourceStackMock.from(caller), "vanish list");

        assertThat(lastMessage(caller)).contains("vanish.list-empty");
    }

    private void execute(CommandSourceStack source, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        try {
            dispatcher.execute(input, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private static String lastMessage(PlayerMock player) {
        String last = null;
        String next;
        while ((next = player.nextMessage()) != null) {
            last = next;
        }
        return last == null ? "" : PLAIN.serialize(MiniMessage.miniMessage().deserialize(last));
    }

    /** A scheduler that runs every task inline so the entity-thread hop fires at once. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Echoes the resolved key and placeholders so the rendered reply is assertable. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            StringBuilder out = new StringBuilder(key.key());
            placeholders.forEach(
                    (name, value) -> out.append(' ').append(name).append('=').append(value));
            return out.toString();
        }
    }

    private static final class DiscardingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // discarded: the target's own confirmation is not under test here
        }
    }
}

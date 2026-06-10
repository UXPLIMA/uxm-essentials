package com.uxplima.uxmessentials.bootstrap.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
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
 * MockBukkit coverage of {@code /help} through its real Brigadier node. The listing reads the supplied
 * registration set, filters each line by the sender's permission (the node's own Brigadier requirement),
 * renders it in the sender's locale, and paginates. The guard proves a command the sender lacks the node
 * for never appears, a held command does, the body is alphabetised, and a query narrows the page.
 */
class HelpCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Reader");
        // /help itself defaults true, so grant it explicitly under MockBukkit's deny-by-default sender.
        player.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.help", true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLiteralIsHelp() {
        assertThat(help(List::of).build().getLiteral()).isEqualTo("help");
    }

    @Test
    void listsOnlyTheCommandsTheSenderMayUseAlphabetised() {
        grant("uxmessentials.home.use");
        grant("uxmessentials.warp.use");
        HelpCommand command = help(() -> List.of(
                fake("warp", "uxmessentials.warp.use", "Teleport to a warp"),
                fake("home", "uxmessentials.home.use", "Go to a home"),
                fake("ban", "uxmessentials.moderation.ban", "Ban a player")));

        List<String> lines = run(command, "");

        assertThat(lines.get(0)).contains("help.header").contains("page=1").contains("pages=1");
        // home before warp (alphabetised); the unheld /ban is filtered out entirely.
        assertThat(lines)
                .anySatisfy(line -> assertThat(line).contains("help.entry").contains("command=home"));
        assertThat(lines)
                .anySatisfy(line -> assertThat(line).contains("help.entry").contains("command=warp"));
        assertThat(lines).noneSatisfy(line -> assertThat(line).contains("command=ban"));
        assertThat(indexOfEntry(lines, "home")).isLessThan(indexOfEntry(lines, "warp"));
    }

    @Test
    void anEmptySurfaceRendersTheEmptyNotice() {
        List<String> lines = run(help(List::of), "");
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("help.empty");
    }

    @Test
    void aQueryNarrowsTheListAndAMissReportsNoMatch() {
        grant("uxmessentials.home.use");
        grant("uxmessentials.warp.use");
        HelpCommand command = help(() -> List.of(
                fake("home", "uxmessentials.home.use", "Go to a home"),
                fake("warp", "uxmessentials.warp.use", "Teleport to a warp")));

        List<String> matched = run(command, "warp");
        assertThat(matched).anySatisfy(line -> assertThat(line).contains("command=warp"));
        assertThat(matched).noneSatisfy(line -> assertThat(line).contains("command=home"));

        List<String> missed = run(command, "nonsense");
        assertThat(missed).hasSize(1);
        assertThat(missed.get(0)).contains("help.no-match").contains("query=nonsense");
    }

    @Test
    void aSecondPageIsReachableByNumberAndCarriesAFooter() {
        List<CommandRegistration> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String node = "uxmessentials.bulk" + i + ".use";
            grant(node);
            many.add(fake(String.format("cmd%02d", i), node, "Bulk command " + i));
        }
        HelpCommand command = help(() -> many);

        List<String> page2 = run(command, "2");
        assertThat(page2.get(0)).contains("help.header").contains("page=2").contains("pages=2");
        // 12 commands over a page size of 8 leaves 4 on page two, then the footer.
        assertThat(page2)
                .anySatisfy(line -> assertThat(line).contains("help.footer").contains("page=2"));
        assertThat(page2).anySatisfy(line -> assertThat(line).contains("command=cmd08"));
        assertThat(page2).noneSatisfy(line -> assertThat(line).contains("command=cmd00"));
    }

    private void grant(String node) {
        player.addAttachment(MockBukkit.createMockPlugin(), node, true);
    }

    private HelpCommand help(Supplier<List<CommandRegistration>> commands) {
        return new HelpCommand(commands, new EchoMessages());
    }

    private List<String> run(HelpCommand command, String argument) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        String input = argument.isEmpty() ? "help" : "help " + argument;
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
        List<String> lines = new ArrayList<>();
        net.kyori.adventure.text.Component message;
        while ((message = player.nextComponentMessage()) != null) {
            lines.add(PLAIN.serialize(message));
        }
        return lines;
    }

    private static int indexOfEntry(List<String> lines, String command) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("help.entry") && lines.get(i).contains("command=" + command)) {
                return i;
            }
        }
        throw new AssertionError("no entry line for /" + command + " in " + lines);
    }

    /** A bare {@link CommandRegistration} whose root gates on {@code permission}, like a real command. */
    private static CommandRegistration fake(String literal, String permission, String description) {
        return new CommandRegistration() {
            @Override
            public LiteralCommandNode<CommandSourceStack> build() {
                return Commands.literal(literal)
                        .requires(src -> src.getSender().hasPermission(permission))
                        .build();
            }

            @Override
            public String description() {
                return description;
            }
        };
    }

    /** Echoes the catalog key and its placeholders as one line so the rendered reply is assertable. */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            StringBuilder out = new StringBuilder(key.key());
            placeholders.forEach(
                    (name, value) -> out.append(' ').append(name).append('=').append(value));
            return out.toString();
        }
    }
}

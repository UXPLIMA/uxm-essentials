package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.ArgumentSpec.ArgType;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.MenuOpenCommand;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of typed open-command arguments through real Brigadier dispatch. A {@code gift} spec whose
 * title reads {@code %argument_target% got %argument_amount%} declares two typed arguments; dispatching
 * {@code /gift Steve 5} must open the menu carrying {@code {target=Steve, amount=5}} and render that title as
 * {@code Steve got 5}. A wrong-typed value is rejected as a syntax error before the menu opens, a zero-argument
 * command still opens, and the material/world/online-player nodes carry their autocomplete.
 */
class MenuArgumentCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final String ARG_SPEC = """
            rows = 1
            title = "%argument_target% got %argument_amount%"
            items { x { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
            """;

    private static final String BARE_SPEC = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
            """;

    private static final String SAY_SPEC = """
            rows = 1
            title = "%argument_message%"
            items { x { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
            """;

    private ServerMock server;
    private Menus menus;
    private MenuRenderer renderer;
    private KeyMessages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        menus = new Menus(renderer, new SyncScheduler(), new ListSourceRegistry());
        menus.registerSpec("gift", new MenuSpecLoader().parse(ARG_SPEC));
        menus.registerSpec("shop", new MenuSpecLoader().parse(BARE_SPEC));
        menus.registerSpec("say", new MenuSpecLoader().parse(SAY_SPEC));
        messages = new KeyMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void dispatchingTheCommandOpensTheMenuCarryingTheTypedArguments() {
        PlayerMock steve = server.addPlayer("Steve");
        PlayerMock buyer = server.addPlayer("Buyer");

        execute(giftCommand(), "gift Steve 5", buyer);

        MenuHolder holder = openHolder(buyer);
        assertThat(holder.specId()).isEqualTo("gift");
        assertThat(holder.ctx().arguments()).containsOnly(Map.entry("target", "Steve"), Map.entry("amount", "5"));
        // And the arguments reach rendered menu TEXT: the title expands both %argument_% tokens.
        assertThat(PLAIN.serialize(renderer.title(holder.spec(), holder.ctx()))).isEqualTo("Steve got 5");
        assertThat(steve.isOnline()).isTrue();
    }

    @Test
    void aWrongTypedArgumentIsASyntaxErrorAndDoesNotOpenTheMenu() {
        PlayerMock buyer = server.addPlayer("Buyer");
        server.addPlayer("Steve");
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcherFor(giftCommand());

        assertThatThrownBy(() -> dispatcher.execute("gift Steve xyz", CommandSourceStackMock.from(buyer)))
                .isInstanceOf(CommandSyntaxException.class);
        assertThat(hasMenuOpen(buyer)).isFalse();
    }

    @Test
    void aZeroArgumentCommandStillOpens() {
        PlayerMock player = server.addPlayer("Anyone");
        OpenCommandSpec bare = new OpenCommandSpec("shop", List.of(), Optional.empty(), Optional.empty(), false);

        execute(new MenuOpenCommand(menus, "shop", bare, messages), "shop", player);

        assertThat(openHolder(player).specId()).isEqualTo("shop");
    }

    @Test
    void theUsageLineBecomesTheCommandDescription() {
        OpenCommandSpec withUsage = new OpenCommandSpec(
                "gift",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of(new ArgumentSpec("amount", ArgType.INT)),
                Optional.of("/gift <amount>"));

        assertThat(new MenuOpenCommand(menus, "gift", withUsage, messages).description())
                .isEqualTo("/gift <amount>");
    }

    @Test
    void materialAndWorldArgumentsAutocompleteFromTheirRegistries() throws Exception {
        PlayerMock player = server.addPlayer("Runner");
        CommandSourceStack source = CommandSourceStackMock.from(player);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcherFor(warptoCommand());

        // Brigadier drops a suggestion identical to the token already typed, so assert on a distinct match.
        assertThat(completions(dispatcher, "warpto diamond", source)).contains("diamond_block");
        // "stone" fills the first (material) word, so completion now sits on the world argument.
        assertThat(completions(dispatcher, "warpto stone ", source)).contains("world");
    }

    @Test
    void theOnlinePlayerNodeUsesThePlayerArgumentTypeAndTheWordNodesCarrySuggestions() {
        CommandNode<CommandSourceStack> target = giftCommand().build().getChild("target");
        assertThat(target).isInstanceOf(ArgumentCommandNode.class);
        assertThat(((ArgumentCommandNode<?, ?>) target).getType().getClass())
                .isEqualTo(ArgumentTypes.player().getClass());

        LiteralCommandNode<CommandSourceStack> warpto = warptoCommand().build();
        assertThat(customSuggestions(warpto.getChild("mat"))).isNotNull();
        assertThat(customSuggestions(warpto.getChild("mat").getChild("dest"))).isNotNull();
    }

    @Test
    void aGreedyLastStringArgumentCapturesTheRestOfTheInputIncludingSpaces() {
        PlayerMock player = server.addPlayer("Speaker");

        execute(sayCommand(true), "say hello there world", player);

        MenuHolder holder = openHolder(player);
        assertThat(holder.ctx().arguments()).containsEntry("message", "hello there world");
        // And the greedy value flows into rendered TEXT the same as any other argument.
        assertThat(PLAIN.serialize(renderer.title(holder.spec(), holder.ctx()))).isEqualTo("hello there world");
    }

    @Test
    void aNonGreedyStringArgumentCapturesOnlyASingleWord() {
        PlayerMock player = server.addPlayer("Speaker");

        execute(sayCommand(false), "say hello", player);

        assertThat(openHolder(player).ctx().arguments()).containsEntry("message", "hello");
    }

    @Test
    void aGreedyLastStringArgumentBuildsAGreedyBrigadierNode() {
        CommandNode<CommandSourceStack> node = sayCommand(true).build().getChild("message");
        ArgumentType<?> type = ((ArgumentCommandNode<?, ?>) node).getType();

        assertThat(type).isInstanceOf(StringArgumentType.class);
        assertThat(((StringArgumentType) type).getType()).isEqualTo(StringArgumentType.StringType.GREEDY_PHRASE);
    }

    @Test
    void aGreedyFlagOnANonLastArgumentIsIgnoredAndFallsBackToASingleWord() {
        OpenCommandSpec spec = new OpenCommandSpec(
                "say",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of(new ArgumentSpec("message", ArgType.STRING, true), new ArgumentSpec("amount", ArgType.INT)),
                Optional.empty());
        CommandNode<CommandSourceStack> node =
                new MenuOpenCommand(menus, "say", spec, messages).build().getChild("message");
        ArgumentType<?> type = ((ArgumentCommandNode<?, ?>) node).getType();

        assertThat(((StringArgumentType) type).getType()).isEqualTo(StringArgumentType.StringType.SINGLE_WORD);
    }

    private MenuOpenCommand sayCommand(boolean greedy) {
        OpenCommandSpec spec = new OpenCommandSpec(
                "say",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of(new ArgumentSpec("message", ArgType.STRING, greedy)),
                Optional.empty());
        return new MenuOpenCommand(menus, "say", spec, messages);
    }

    private MenuOpenCommand giftCommand() {
        OpenCommandSpec spec = new OpenCommandSpec(
                "gift",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of(new ArgumentSpec("target", ArgType.ONLINE_PLAYER), new ArgumentSpec("amount", ArgType.INT)),
                Optional.empty());
        return new MenuOpenCommand(menus, "gift", spec, messages);
    }

    private MenuOpenCommand warptoCommand() {
        OpenCommandSpec spec = new OpenCommandSpec(
                "warpto",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of(new ArgumentSpec("mat", ArgType.MATERIAL), new ArgumentSpec("dest", ArgType.WORLD)),
                Optional.empty());
        return new MenuOpenCommand(menus, "gift", spec, messages);
    }

    private static Object customSuggestions(CommandNode<CommandSourceStack> node) {
        return ((ArgumentCommandNode<?, ?>) node).getCustomSuggestions();
    }

    private static void execute(MenuOpenCommand command, String input, PlayerMock who) {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcherFor(command);
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(who));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private static CommandDispatcher<CommandSourceStack> dispatcherFor(MenuOpenCommand command) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        return dispatcher;
    }

    private static List<String> completions(
            CommandDispatcher<CommandSourceStack> dispatcher, String input, CommandSourceStack source)
            throws Exception {
        ParseResults<CommandSourceStack> parse = dispatcher.parse(input, source);
        Suggestions suggestions = dispatcher.getCompletionSuggestions(parse).get();
        return suggestions.getList().stream().map(Suggestion::getText).toList();
    }

    private static MenuHolder openHolder(PlayerMock who) {
        assertThat(who.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
        return (MenuHolder) who.getOpenInventory().getTopInventory().getHolder();
    }

    /** Null-safe check for whether {@code who} has an engine menu open (the top inventory may be null when none is). */
    private static boolean hasMenuOpen(PlayerMock who) {
        var top = who.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    /** A pass-through messages double: returns the key verbatim so no catalog is needed. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class SyncScheduler implements Scheduler {
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
}

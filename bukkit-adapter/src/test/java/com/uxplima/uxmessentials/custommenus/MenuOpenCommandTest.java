package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
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
 * MockBukkit coverage of an operator-declared open command through its real Brigadier node. A {@code shop} spec is
 * registered; a command block naming {@code /shop} (alias {@code /store}), permission {@code srv.shop} and a deny
 * message must open for a permitted player, draw the rendered deny line for one without the node, and turn the
 * console away — while a bare permissionless block opens for anyone.
 */
class MenuOpenCommandTest {

    private static final String SPEC_HOCON = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
            """;

    private ServerMock server;
    private Menus menus;
    private RecordingMessages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        menus = new Menus(renderer, new SyncScheduler(), new ListSourceRegistry());
        MenuSpec spec = new MenuSpecLoader().parse(SPEC_HOCON);
        menus.registerSpec("shop", spec);
        messages = new RecordingMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theBuiltLiteralAndAliasesMirrorTheSpec() {
        MenuOpenCommand command = command(gatedSpec());

        assertThat(command.build().getLiteral()).isEqualTo("shop");
        assertThat(command.aliases()).containsExactly("store");
    }

    @Test
    void aPlayerHoldingThePermissionOpensTheMenu() {
        PlayerMock player = server.addPlayer("Buyer");
        player.addAttachment(MockBukkit.createMockPlugin(), "srv.shop", true);

        execute(gatedSpec(), "shop", player);

        assertThat(openMenuIdFor(player)).contains("shop");
    }

    @Test
    void aPlayerWithoutThePermissionGetsTheDenyMessageAndStaysOut() {
        PlayerMock player = server.addPlayer("NoBuyer");

        execute(gatedSpec(), "shop", player);

        assertThat(openMenuIdFor(player)).isEmpty();
        assertThat(player.nextMessage()).contains("nope");
    }

    @Test
    void aMissingDenyMessageFallsBackToTheSharedNoPermissionLine() {
        PlayerMock player = server.addPlayer("NoBuyer");
        OpenCommandSpec spec = new OpenCommandSpec("shop", List.of(), Optional.of("srv.shop"), Optional.empty(), false);

        execute(spec, "shop", player);

        assertThat(openMenuIdFor(player)).isEmpty();
        assertThat(messages.keys).contains("command.no-permission");
    }

    @Test
    void theConsoleIsTurnedAwayWhenTheBlockForbidsIt() {
        execute(gatedSpec(), "shop", server.getConsoleSender());

        assertThat(messages.keys).contains("menu.console-denied");
    }

    @Test
    void aConsoleAllowedBlockStillCannotOpenForItselfWithoutAPlayer() {
        OpenCommandSpec spec = new OpenCommandSpec("shop", List.of(), Optional.empty(), Optional.empty(), true);

        execute(spec, "shop", server.getConsoleSender());

        assertThat(messages.keys).contains("command.players-only");
    }

    @Test
    void aBarePermissionlessBlockOpensForAnyPlayer() {
        PlayerMock player = server.addPlayer("Anyone");
        OpenCommandSpec spec = new OpenCommandSpec("shop", List.of(), Optional.empty(), Optional.empty(), false);

        execute(spec, "shop", player);

        assertThat(openMenuIdFor(player)).contains("shop");
    }

    private static OpenCommandSpec gatedSpec() {
        return new OpenCommandSpec("shop", List.of("store"), Optional.of("srv.shop"), Optional.of("<red>nope"), false);
    }

    private MenuOpenCommand command(OpenCommandSpec spec) {
        return new MenuOpenCommand(menus, "shop", spec, messages);
    }

    private void execute(OpenCommandSpec spec, String input, CommandSender who) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command(spec).build());
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(who));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    /** The menu id of {@code who}'s open holder-backed window, or empty when no engine menu is open. */
    private static Optional<String> openMenuIdFor(PlayerMock who) {
        var top = who.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder holder
                ? Optional.of(holder.specId())
                : Optional.empty();
    }

    /** Records each delivered catalog key so a path's outcome is read off the line it produced. */
    private static final class RecordingMessages implements Messages {
        private final List<String> keys = new ArrayList<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key.key());
            return key.key();
        }
    }

    /** A pass-through messages double for the GuiText the renderer leans on. */
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

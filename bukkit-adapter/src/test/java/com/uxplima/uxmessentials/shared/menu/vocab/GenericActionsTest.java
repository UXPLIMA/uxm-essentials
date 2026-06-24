package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the generic (non-console) menu action vocabulary. Each registered handler is fetched
 * back through {@link MenuBindings#action(String)} and invoked with a {@link MenuActionContext} built the same
 * way the click listener builds it, then the effect on the live MockBukkit player is asserted: a closed window,
 * a player-run command, a chat message, a sound, or a second menu opened. This is the contract Phase-2 operator
 * menus and our own feature menus both lean on.
 */
class GenericActionsTest {

    private static final String OPEN_TARGET_HOCON = """
            rows = 1
            items {
              one { slot = 0, material = STONE, name = "@n" }
            }
            """;

    private ServerMock server;
    private PlayerMock player;
    private MenuBindings bindings;
    private Menus menus;
    private RecordingLogger log;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Viewer");
        GuiText guiText = new GuiText(new KeyMessages());
        bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        menus = new Menus(renderer, guiText, new SyncScheduler(), new ListSourceRegistry());
        MenuSpec target = new MenuSpecLoader().parse(OPEN_TARGET_HOCON);
        menus.registerSpec("target", target);
        log = new RecordingLogger();
        MenuVocabulary.registerActions(bindings, menus, true, log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void closeShutsTheOpenWindow() {
        player.openInventory(server.createInventory(player, 9));
        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(9);

        invoke("close", "");

        assertThat(player.getOpenInventory().getType().name()).contains("CRAFTING");
    }

    @Test
    void commandRunsAsThePlayer() {
        server.getCommandMap().register("test", new EchoCommand());

        invoke("command", "echo hi there");

        assertThat(player.nextMessage()).isEqualTo("echoed: hi there");
    }

    @Test
    void messageSendsRenderedChat() {
        invoke("message", "<green>hello world");

        assertThat(player.nextMessage()).contains("hello world");
    }

    @Test
    void soundPlaysForThePlayerWithoutThrowing() {
        assertThatCode(() -> invoke("sound", "block.note_block.pling")).doesNotThrowAnyException();
        assertThat(player.getHeardSounds()).isNotEmpty();
    }

    @Test
    void blankSoundIsANoOp() {
        invoke("sound", "");

        assertThat(player.getHeardSounds()).isEmpty();
    }

    @Test
    void openShowsTheTargetSpecInAMenuHolderWindow() {
        invoke("open", "target");

        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        assertThat(holder).isInstanceOf(MenuHolder.class);
    }

    @Test
    void consoleEnabledDispatches() {
        FlagCommand flag = new FlagCommand();
        server.getCommandMap().register("test", flag);

        invoke("console", "flagme");

        assertThat(flag.ran)
                .as("console action should dispatch when allow-console is true")
                .isTrue();
        assertThat(log.warnings)
                .as("dispatching console action should not warn")
                .isEmpty();
    }

    @Test
    void consoleDisabledIsANoOpAndWarns() {
        bindings = new MenuBindings();
        log = new RecordingLogger();
        MenuVocabulary.registerActions(bindings, menus, false, log);
        FlagCommand flag = new FlagCommand();
        server.getCommandMap().register("test", flag);

        invoke("console", "flagme");

        assertThat(flag.ran)
                .as("disabled console action must not run the command")
                .isFalse();
        assertThat(log.warnings).as("disabled console action must warn once").hasSize(1);
        assertThat(log.warnings.getFirst()).contains("flagme");
    }

    /** Builds the context the click listener would build, then fires the handler registered under {@code id}. */
    private void invoke(String id, String arg) {
        Consumer<MenuActionContext> handler =
                bindings.action(id).orElseThrow(() -> new AssertionError("action not registered: " + id));
        PlayerRef ref = new PlayerRef(player.getUniqueId(), player.getName());
        MenuActionContext ctx =
                new MenuActionContext(MenuContext.of(ref, null, 0), player, ClickKind.LEFT, Map.of("value", arg));
        handler.accept(ctx);
    }

    /** A throwaway command that echoes its joined args back to the runner, proving the player ran it. */
    private static final class EchoCommand extends Command {
        EchoCommand() {
            super("echo");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            sender.sendMessage("echoed: " + String.join(" ", args));
            return true;
        }
    }

    /** A console-dispatched command that flips a flag, so a test can prove whether the console actually ran it. */
    private static final class FlagCommand extends Command {
        private boolean ran;

        FlagCommand() {
            super("flagme");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            ran = true;
            return true;
        }
    }

    /** Captures expanded warning lines so a test can assert a disabled console action logged exactly once. */
    private static final class RecordingLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            String expanded = message;
            for (Object arg : args) {
                expanded = expanded.replaceFirst("\\{}", String.valueOf(arg));
            }
            warnings.add(expanded);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}

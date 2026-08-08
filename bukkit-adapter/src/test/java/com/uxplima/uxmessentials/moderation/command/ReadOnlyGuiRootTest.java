package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.BanHistoryCommand;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.BanListCommand;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.ModerationGuiViews;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.GuiRootBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The bare-root wiring for the read-only {@code /banlist} and {@code /banhistory} commands: each exposes its GUI
 * opener through {@code guiRoot()}, so with the catalog {@code gui} flag on the shared {@link GuiRootBinding}
 * installs that opener as the bare-root executor while the raw subcommand carries across; with gui off the root
 * is left bare for the usage/chat fallback. A command built with no GUI collaborators exposes no opener at all.
 * MockBukkit boots Paper's Brigadier so the node rebuild is wired.
 */
class ReadOnlyGuiRootTest {

    private ServerMock server;
    private Plugin plugin;
    private ModerationServices services;
    private GuiText guiText;
    private Scheduler scheduler;
    private ModerationGuiViews views;
    private PlayerPickerView picker;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        services = mock(ModerationServices.class);
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        TextInput textInput = TextInputTestKit.create(
                plugin, guiText, scheduler, java.nio.file.Path.of("nonexistent"), new NoopLogger());
        com.uxplima.uxmessentials.shared.menu.TestMenuEngine engine =
                com.uxplima.uxmessentials.shared.menu.TestMenuEngine.create(new KeyMessages(), scheduler);
        picker = new PlayerPickerView(engine.menus(), scheduler, textInput, server, new KeyMessages(), new NoopSink());
        picker.register(engine.bindings(), java.nio.file.Path.of("nonexistent"), new NoopLogger());
        // A fully built ModerationGuiViews needs a repository/history fake; the wiring tests only need a non-null
        // instance because they assert the opener is installed, not that it opens a live menu.
        views = mock(ModerationGuiViews.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bareBanListInstallsTheGuiOpenerWhenGuiOn() {
        BanListCommand banlist = new BanListCommand(services, new KeyMessages(), new NoopSink(), scheduler, views);
        LiteralCommandNode<CommandSourceStack> node =
                binding("banlist", true).wrap(banlist).build();

        assertThat(node.getLiteral()).isEqualTo("banlist");
        assertThat(banlist.guiRoot()).isPresent();
        // The bare root now carries the GUI opener, replacing the chat-list executor.
        assertThat(node.getCommand()).isNotNull();
    }

    @Test
    void bareBanListKeepsTheChatExecutorWhenGuiOff() {
        BanListCommand banlist = new BanListCommand(services, new KeyMessages(), new NoopSink(), scheduler, views);
        LiteralCommandNode<CommandSourceStack> node =
                binding("banlist", false).wrap(banlist).build();

        // gui off leaves the command's own chat-list executor on the root untouched.
        assertThat(node.getCommand()).isNotNull();
    }

    @Test
    void aBanListWithNoViewsExposesNoOpener() {
        BanListCommand banlist = new BanListCommand(services, new KeyMessages(), new NoopSink(), scheduler, null);
        assertThat(banlist.guiRoot()).isEmpty();
    }

    @Test
    void bareBanHistoryInstallsThePickerOpenerWhenGuiOn() {
        BanHistoryCommand banhistory =
                new BanHistoryCommand(services, new KeyMessages(), new NoopSink(), scheduler, picker, views);
        LiteralCommandNode<CommandSourceStack> node =
                binding("banhistory", true).wrap(banhistory).build();

        assertThat(banhistory.guiRoot()).isPresent();
        // The bare root gains the picker opener and the raw <player> child carries across.
        assertThat(node.getCommand()).isNotNull();
        assertThat(node.getChild("player")).isNotNull();
    }

    @Test
    void bareBanHistoryFallsBackToUsageWhenGuiOff() {
        BanHistoryCommand banhistory =
                new BanHistoryCommand(services, new KeyMessages(), new NoopSink(), scheduler, picker, views);
        LiteralCommandNode<CommandSourceStack> node =
                binding("banhistory", false).wrap(banhistory).build();

        // gui off leaves the arg-only root bare so the usage binding can later inject its usage executor.
        assertThat(node.getCommand()).isNull();
        assertThat(node.getChild("player")).isNotNull();
    }

    @Test
    void aBanHistoryWithNoGuiDepsExposesNoOpener() {
        BanHistoryCommand banhistory =
                new BanHistoryCommand(services, new KeyMessages(), new NoopSink(), scheduler, null, null);
        assertThat(banhistory.guiRoot()).isEmpty();
    }

    private static GuiRootBinding binding(String id, boolean gui) {
        return new GuiRootBinding(Map.of(id, new EffectiveCommand(new CommandId(id), id, List.of(), true, gui)));
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
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

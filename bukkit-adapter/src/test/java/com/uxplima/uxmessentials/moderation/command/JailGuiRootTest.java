package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.JailCommand;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.JailedPlayersCommand;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.JailsCommand;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.JailGuiViews;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.GuiRootBinding;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The bare-root wiring for the three jail commands (capability D): each exposes its GUI opener through
 * {@code guiRoot()}, so with the catalog {@code gui} flag on the shared {@link GuiRootBinding} installs that
 * opener as the bare-root executor while the raw subcommand carries across; with gui off the root is left for
 * the chat/usage fallback. A command built with no {@link JailGuiViews} exposes no opener at all. MockBukkit
 * boots Paper's Brigadier so the node rebuild is wired.
 */
class JailGuiRootTest {

    private ModerationServices services;
    private Scheduler scheduler;
    private JailGuiViews jailGui;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        MockBukkit.createMockPlugin();
        services = mock(ModerationServices.class);
        scheduler = new SyncScheduler();
        jailGui = mock(JailGuiViews.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bareJailInstallsTheHubOpenerWhenGuiOn() {
        JailCommand jail = new JailCommand(services, new KeyMessages(), new NoopSink(), jailGui);
        LiteralCommandNode<CommandSourceStack> node =
                binding("jail", true).wrap(jail).build();

        assertThat(node.getLiteral()).isEqualTo("jail");
        assertThat(jail.guiRoot()).isPresent();
        // The bare root gains the hub opener and the raw <player> child carries across.
        assertThat(node.getCommand()).isNotNull();
        assertThat(node.getChild("player")).isNotNull();
    }

    @Test
    void bareJailFallsBackToUsageWhenGuiOff() {
        JailCommand jail = new JailCommand(services, new KeyMessages(), new NoopSink(), jailGui);
        LiteralCommandNode<CommandSourceStack> node =
                binding("jail", false).wrap(jail).build();

        // gui off leaves the arg-only root bare so the usage binding can later inject its usage executor.
        assertThat(node.getCommand()).isNull();
        assertThat(node.getChild("player")).isNotNull();
    }

    @Test
    void aJailWithNoGuiExposesNoOpener() {
        JailCommand jail = new JailCommand(services, new KeyMessages(), new NoopSink(), null);
        assertThat(jail.guiRoot()).isEmpty();
    }

    @Test
    void bareJailsInstallsTheJailListOpenerWhenGuiOn() {
        JailsCommand jails = new JailsCommand(services, new KeyMessages(), new NoopSink(), scheduler, jailGui);
        LiteralCommandNode<CommandSourceStack> node =
                binding("jails", true).wrap(jails).build();

        assertThat(jails.guiRoot()).isPresent();
        assertThat(node.getCommand()).isNotNull();
    }

    @Test
    void bareJailsKeepsTheChatExecutorWhenGuiOff() {
        JailsCommand jails = new JailsCommand(services, new KeyMessages(), new NoopSink(), scheduler, jailGui);
        LiteralCommandNode<CommandSourceStack> node =
                binding("jails", false).wrap(jails).build();

        // gui off leaves the command's own chat-list executor on the root untouched.
        assertThat(node.getCommand()).isNotNull();
    }

    @Test
    void aJailsWithNoGuiExposesNoOpener() {
        JailsCommand jails = new JailsCommand(services, new KeyMessages(), new NoopSink(), scheduler, null);
        assertThat(jails.guiRoot()).isEmpty();
    }

    @Test
    void bareJailedPlayersInstallsTheReleaseListOpenerWhenGuiOn() {
        JailedPlayersCommand jailed =
                new JailedPlayersCommand(services, new KeyMessages(), new NoopSink(), scheduler, jailGui);
        LiteralCommandNode<CommandSourceStack> node =
                binding("jailedplayers", true).wrap(jailed).build();

        assertThat(jailed.guiRoot()).isPresent();
        assertThat(node.getCommand()).isNotNull();
    }

    @Test
    void aJailedPlayersWithNoGuiExposesNoOpener() {
        JailedPlayersCommand jailed =
                new JailedPlayersCommand(services, new KeyMessages(), new NoopSink(), scheduler, null);
        assertThat(jailed.guiRoot()).isEmpty();
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

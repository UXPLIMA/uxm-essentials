package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.BanCommand;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.MuteCommand;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentConfirmView;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentGuiFlow;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.GuiRootBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.DurationPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
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
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The bare {@code /ban} and {@code /mute} GUI wiring: each exposes its picker→confirm opener through
 * {@code guiRoot()}, so with the catalog {@code gui} flag on the shared {@link GuiRootBinding} installs that
 * opener as the bare-root executor while the raw subcommand children carry across; with gui off the root is left
 * bare for the usage fallback. A command built with no GUI flow exposes no opener at all. MockBukkit boots
 * Paper's Brigadier so the node rebuild is wired.
 */
class BanMuteGuiRootTest {

    private ServerMock server;
    private Plugin plugin;
    private ModerationServices services;
    private PunishmentGuiFlow flow;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        services = mock(ModerationServices.class);
        GuiText guiText = new GuiText(new KeyMessages());
        Scheduler scheduler = new SyncScheduler();
        com.uxplima.uxmlib.gui.anvil.AnvilInput anvil = new com.uxplima.uxmlib.gui.anvil.AnvilInput(plugin);
        PlayerPickerView picker =
                new PlayerPickerView(guiText, scheduler, anvil, server, new KeyMessages(), new NoopSink());
        DurationPickerView durations =
                new DurationPickerView(guiText, scheduler, anvil, new KeyMessages(), new NoopSink());
        PunishmentConfirmView confirm = new PunishmentConfirmView(guiText, scheduler, anvil);
        flow = new PunishmentGuiFlow(services, picker, durations, confirm, new KeyMessages(), new NoopSink());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bareBanInstallsThePickerOpenerWhenGuiOn() {
        BanCommand ban = new BanCommand(services, new KeyMessages(), new NoopSink(), false, flow);
        LiteralCommandNode<CommandSourceStack> node =
                binding("ban", true).wrap(ban).build();

        assertThat(node.getLiteral()).isEqualTo("ban");
        // The command exposes an opener and the bare root now carries an executor; the raw <player> child stays.
        assertThat(ban.guiRoot()).isPresent();
        assertThat(node.getCommand()).isNotNull();
        assertThat(node.getChild("player")).isNotNull();
    }

    @Test
    void bareMuteInstallsThePickerOpenerWhenGuiOn() {
        MuteCommand mute = new MuteCommand(services, new KeyMessages(), new NoopSink(), false, flow);
        LiteralCommandNode<CommandSourceStack> node =
                binding("mute", true).wrap(mute).build();

        assertThat(mute.guiRoot()).isPresent();
        assertThat(node.getCommand()).isNotNull();
        assertThat(node.getChild("player")).isNotNull();
    }

    @Test
    void bareBanFallsBackToUsageWhenGuiOff() {
        BanCommand ban = new BanCommand(services, new KeyMessages(), new NoopSink(), false, flow);
        LiteralCommandNode<CommandSourceStack> node =
                binding("ban", false).wrap(ban).build();

        // gui off leaves the arg-only root bare so the usage binding can later inject its usage executor.
        assertThat(node.getCommand()).isNull();
        assertThat(node.getChild("player")).isNotNull();
    }

    @Test
    void aBanWithNoFlowExposesNoOpener() {
        BanCommand ban = new BanCommand(services, new KeyMessages(), new NoopSink(), false, null);
        assertThat(ban.guiRoot()).isEmpty();
        // With no opener the node is returned untouched even when gui is on.
        assertThat(binding("ban", true).wrap(ban).build().getCommand()).isNull();
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
        public void asyncAfter(java.time.Duration delay, Runnable task) {
            task.run();
        }
    }
}

package com.uxplima.uxmessentials.shared.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.adapter.inbound.command.HologramCommand;
import com.uxplima.uxmessentials.holograms.adapter.inbound.gui.HologramListMenu;
import com.uxplima.uxmessentials.holograms.application.CreateHologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.SetJailCommand;
import com.uxplima.uxmessentials.moderation.application.SetJail;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.inbound.command.NpcCommand;
import com.uxplima.uxmessentials.npc.adapter.inbound.command.NpcSkinByName;
import com.uxplima.uxmessentials.npc.adapter.inbound.gui.NpcListMenu;
import com.uxplima.uxmessentials.npc.application.CreateNpc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.adapter.inbound.command.AdminTpCommand;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import com.uxplima.uxmessentials.warps.adapter.inbound.command.WarpCommand;
import com.uxplima.uxmessentials.worlds.adapter.WorldsServices;
import com.uxplima.uxmessentials.worlds.adapter.inbound.command.WorldCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockito.ArgumentCaptor;

/** Pins the explicit-location NPC and hologram setup paths used by console automation. */
class ConsolePlacementCommandTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void consoleCreatesNpcAtExplicitPosition() {
        NpcServices services = mock(NpcServices.class);
        CreateNpc create = mock(CreateNpc.class);
        when(services.create()).thenReturn(create);
        NpcCommand command = new NpcCommand(
                services, List::of, mock(NpcSkinByName.class), mock(Messages.class), mock(NpcListMenu.class));

        assertParses(command.build(), "npc createat legacy world 10 65 -4");
        execute(command.build(), "npc create guide at world 10 65 -4");

        ArgumentCaptor<PlayerRef> actor = ArgumentCaptor.forClass(PlayerRef.class);
        ArgumentCaptor<Position> position = ArgumentCaptor.forClass(Position.class);
        verify(create)
                .create(
                        actor.capture(),
                        any(NpcName.class),
                        position.capture(),
                        isNull(NpcSkin.class),
                        isNull(String.class));
        assertThat(actor.getValue().isSystem()).isTrue();
        assertSystemPosition(position.getValue(), 10, 65, -4);
    }

    @Test
    void consoleCreatesHologramAtExplicitPosition() {
        HologramServices services = mock(HologramServices.class);
        CreateHologram create = mock(CreateHologram.class);
        when(services.create()).thenReturn(create);
        HologramCommand command =
                new HologramCommand(services, mock(Messages.class), List::of, List::of, mock(HologramListMenu.class));

        assertParses(command.build(), "hologram createat legacy world 1 70 2 Legacy text");
        execute(command.build(), "hologram create welcome at world 1 70 2 Hello world");

        ArgumentCaptor<PlayerRef> actor = ArgumentCaptor.forClass(PlayerRef.class);
        ArgumentCaptor<Position> position = ArgumentCaptor.forClass(Position.class);
        verify(create).create(actor.capture(), any(HologramName.class), position.capture(), any(HologramLine.class));
        assertThat(actor.getValue().isSystem()).isTrue();
        assertSystemPosition(position.getValue(), 1, 70, 2);
    }

    @Test
    void consoleDefinesJailAtExplicitPosition() {
        ModerationServices services = mock(ModerationServices.class);
        SetJail setJail = mock(SetJail.class);
        when(services.setJail()).thenReturn(setJail);
        SetJailCommand command = new SetJailCommand(services, mock(Messages.class), (viewer, rendered) -> {});

        execute(command.build(), "setjail spawn at world 3 64 7 90 0");

        ArgumentCaptor<PlayerRef> actor = ArgumentCaptor.forClass(PlayerRef.class);
        ArgumentCaptor<Position> position = ArgumentCaptor.forClass(Position.class);
        verify(setJail).set(actor.capture(), org.mockito.ArgumentMatchers.eq("spawn"), position.capture());
        assertThat(actor.getValue().isSystem()).isTrue();
        assertSystemPosition(position.getValue(), 3, 64, 7);
        assertThat(position.getValue().yaw()).isEqualTo(90f);
    }

    @Test
    void consoleTeleportsExplicitPlayerToExplicitPosition() {
        org.mockbukkit.mockbukkit.entity.PlayerMock target = server.addPlayer("Bob");
        TeleportServices services = mock(TeleportServices.class);
        TeleportExecutor executor = mock(TeleportExecutor.class);
        when(services.executor()).thenReturn(executor);
        when(services.notifier()).thenReturn(mock(com.uxplima.uxmessentials.shared.application.message.Notifier.class));
        AdminTpCommand command = new AdminTpCommand(
                services, mock(Messages.class), "tp", "uxmessentials.tp.use", "Teleport", AdminTpCommand.Pull.GO);

        execute(command.build(), "tp at Bob world 5 70 -2");

        ArgumentCaptor<Destination> destination = ArgumentCaptor.forClass(Destination.class);
        verify(executor)
                .teleport(
                        org.mockito.ArgumentMatchers.eq(
                                com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs.toRef(target)),
                        destination.capture(),
                        org.mockito.ArgumentMatchers.eq(TeleportKind.ADMIN));
        assertSystemPosition(destination.getValue().position(), 5, 70, -2);
    }

    @Test
    void canonicalWarpAndWorldPlacementFormsParseForConsoleAutomation() {
        WarpCommand warp = new WarpCommand(
                mock(WarpServices.class),
                mock(Messages.class),
                () -> com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode.CHAT);
        assertParses(warp.build(), "warp create spawn at world 0 64 0");
        assertParses(warp.build(), "warp move spawn at world 1 65 1");
        assertParses(warp.build(), "warp createat legacy world 0 64 0");

        WorldCommand worlds = new WorldCommand(mock(WorldsServices.class), mock(Messages.class));
        assertParses(worlds.build(), "worlds setspawn world at 0 64 0 90 0");
        assertParses(worlds.build(), "worlds setspawn world 0 64 0");
    }

    private void execute(com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> node, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(server.getConsoleSender()));
        } catch (CommandSyntaxException syntax) {
            throw new AssertionError("command did not parse: " + input, syntax);
        }
    }

    private void assertParses(com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> node, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        var parsed = dispatcher.parse(input, CommandSourceStackMock.from(server.getConsoleSender()));
        assertThat(parsed.getReader().getRemaining()).as(input).isEmpty();
        assertThat(parsed.getContext().getCommand()).as(input).isNotNull();
    }

    private static void assertSystemPosition(Position actual, double x, double y, double z) {
        assertThat(actual.world().name()).isEqualTo("world");
        assertThat(actual.x()).isEqualTo(x);
        assertThat(actual.y()).isEqualTo(y);
        assertThat(actual.z()).isEqualTo(z);
    }
}

package com.uxplima.uxmessentials.regions.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionListView;
import com.uxplima.uxmessentials.regions.adapter.outbound.NoWorldGuardRegionService;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionMemberChange;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /regions [world]}: the command is gated by {@code uxmessentials.regions.list}; on a
 * server without WorldGuard (the no-op {@link RegionService}) it replies "WorldGuard not installed" and opens no
 * window; with WorldGuard present an unknown world is refused, and the current world opens the region list.
 */
class RegionsCommandTest {

    private static final String PERMISSION = "uxmessentials.regions.list";

    private ServerMock server;
    private Plugin plugin;
    private Scheduler scheduler;
    private Messages messages;
    private GuiText guiText;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        scheduler = new SyncScheduler();
        messages = keyEcho();
        guiText = new GuiText(messages);
        TestMenuEngine engine = TestMenuEngine.create(messages, scheduler);
        engine.installListener(plugin);
        menus = engine.menus();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theCommandIsGatedByTheListPermission() {
        RegionsCommand command = command(new NoWorldGuardRegionService());
        PlayerMock permitted = server.addPlayer("Permitted");
        permitted.addAttachment(plugin, PERMISSION, true);
        PlayerMock denied = server.addPlayer("Denied");

        assertThat(command.build().getRequirement().test(CommandSourceStackMock.from(permitted)))
                .isTrue();
        assertThat(command.build().getRequirement().test(CommandSourceStackMock.from(denied)))
                .isFalse();
    }

    @Test
    void withoutWorldGuardItRepliesNotInstalledAndOpensNoWindow() {
        RegionsCommand command = command(new NoWorldGuardRegionService());
        PlayerMock staff = permittedPlayer("Staff");

        dispatch(command, CommandSourceStackMock.from(staff), "regions");

        assertThat(staff.nextMessage()).contains("regions.no-worldguard");
        assertThat(menuHolderOpen(staff)).isFalse();
    }

    /** Whether {@code player} currently has an engine menu open — null-safe (no window means a null top inventory). */
    private static boolean menuHolderOpen(PlayerMock player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    @Test
    void withWorldGuardAnUnknownWorldIsRefused() {
        RegionsCommand command = command(new FakeRegionService(true));
        PlayerMock staff = permittedPlayer("Staff");

        dispatch(command, CommandSourceStackMock.from(staff), "regions ghostworld");

        assertThat(staff.nextMessage()).contains("regions.unknown-world");
    }

    @Test
    void withWorldGuardTheCurrentWorldOpensTheList() {
        FakeRegionService service = new FakeRegionService(true);
        PlayerMock staff = permittedPlayer("Staff");
        service.add(new RegionRef(
                new WorldRef(staff.getWorld().getUID(), staff.getWorld().getName()), "spawn"));
        RegionsCommand command = command(service);

        dispatch(command, CommandSourceStackMock.from(staff), "regions");

        assertThat(staff.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    private RegionsCommand command(RegionService service) {
        RegionListView listView = new RegionListView(
                menus,
                guiText,
                scheduler,
                messages,
                noopSink(),
                service,
                EntityListLayout.paginatedDefault(Material.PAPER));
        return new RegionsCommand(service, listView, server, messages);
    }

    private PlayerMock permittedPlayer(String name) {
        PlayerMock player = server.addPlayer(name);
        player.addAttachment(plugin, PERMISSION, true);
        return player;
    }

    private static void dispatch(RegionsCommand command, CommandSourceStack source, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        try {
            dispatcher.execute(input, source);
        } catch (CommandSyntaxException syntax) {
            throw new IllegalStateException("command dispatch failed: " + input, syntax);
        }
    }

    private static Messages keyEcho() {
        return (viewer, key, placeholders) -> key.key();
    }

    private static MessageSink noopSink() {
        return (viewer, renderedText) -> {};
    }

    /** An in-memory {@link RegionService} whose reads answer for every world; mutations are out of scope. */
    private static final class FakeRegionService implements RegionService {
        private final boolean available;
        private final List<RegionRef> regions = new java.util.ArrayList<>();

        FakeRegionService(boolean available) {
            this.available = available;
        }

        void add(RegionRef region) {
            regions.add(region);
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public List<RegionRef> regionsIn(WorldRef world) {
            return List.copyOf(regions);
        }

        @Override
        public Optional<RegionRef> region(WorldRef world, String id) {
            return regions.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public List<FlagValue> flags(RegionRef region) {
            return List.of();
        }

        @Override
        public List<String> members(RegionRef region) {
            return List.of();
        }

        @Override
        public List<String> owners(RegionRef region) {
            return List.of();
        }

        @Override
        public int priority(RegionRef region) {
            return 0;
        }

        @Override
        public RegionRef create(WorldRef world, String id, Position min, Position max) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFlag(RegionRef region, FlagValue flag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyMemberChange(RegionMemberChange change) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPriority(RegionRef region, int priority) {
            throw new UnsupportedOperationException();
        }
    }

    /** Runs every scheduler hop inline. */
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

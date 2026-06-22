package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.Unjail;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link JailedPlayersView} (capability C): opening it renders one head per active jail
 * read from the repository, and a click releases that player through the audited {@code Unjail} use case. The
 * repository and use case are Mockito mocks; the synchronous scheduler runs the off-thread read and the
 * entity-bound open inline.
 */
class JailedPlayersViewTest {

    private static final int FIRST_ENTRY_SLOT = 0;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock staff;
    private PlayerRef staffRef;
    private UUID jailedUuid;

    private ModerationServices services;
    private Unjail unjail;
    private ModerationRepository repository;
    private JailedPlayersView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        staff = server.addPlayer("Staff");
        staffRef = new PlayerRef(staff.getUniqueId(), staff.getName());
        jailedUuid = UUID.randomUUID();

        services = mock(ModerationServices.class);
        unjail = mock(Unjail.class);
        repository = mock(ModerationRepository.class);
        PlayerLookup players = mock(PlayerLookup.class);
        when(services.unjail()).thenReturn(unjail);
        when(players.findByUuid(jailedUuid)).thenReturn(Optional.of(new PlayerRef(jailedUuid, "Prisoner")));
        when(repository.activeJails(any(Instant.class), eq(50)))
                .thenReturn(List.of(new JailEntry(
                        jailedUuid,
                        "alcatraz",
                        Issuer.of(staffRef),
                        Optional.of("griefing"),
                        Optional.empty(),
                        Optional.empty())));

        Guis.install(plugin);
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        view = new JailedPlayersView(
                new GuiText(new KeyMessages()),
                new SyncScheduler(),
                services,
                repository,
                players,
                clock,
                EntityListLayout.paginatedDefault(Material.PLAYER_HEAD));
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void rendersAHeadPerActiveJail() {
        view.open(staff, staffRef);

        Inventory menu = staff.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(PaginatedGui.class);
        assertThat(menu.getItem(FIRST_ENTRY_SLOT).getType()).isEqualTo(Material.PLAYER_HEAD);
    }

    @Test
    void clickReleasesThePlayerThroughUnjail() {
        view.open(staff, staffRef);

        fireClick(FIRST_ENTRY_SLOT);

        verify(unjail).unjail(eq(staffRef), eq(new PlayerRef(jailedUuid, "Prisoner")));
    }

    private void fireClick(int slot) {
        InventoryView inventoryView = staff.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                inventoryView, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, java.util.Map<String, String> placeholders) {
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

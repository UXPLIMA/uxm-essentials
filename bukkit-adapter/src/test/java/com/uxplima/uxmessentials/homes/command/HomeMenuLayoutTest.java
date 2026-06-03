package com.uxplima.uxmessentials.homes.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeMenuView;
import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.application.port.MainHomePreference;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.PaginatedGui;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Proves the {@code /homes} menu honours its injected {@link GuiLayout}: a three-row layout opens a 27-slot
 * menu, the unchanged default still opens 54 slots, and the title resolved from
 * {@link HomesMessageKey#HOME_MENU_TITLE} is preserved across layouts.
 */
class HomeMenuLayoutTest {

    private static final String TITLE = HomesMessageKey.HOME_MENU_TITLE.key();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        com.uxplima.uxmlib.gui.Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        com.uxplima.uxmlib.gui.Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void aThreeRowLayoutOpensATwentySevenSlotMenu() {
        openAndReturn(layout(3));

        Inventory menu = player.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(PaginatedGui.class);
        assertThat(menu.getSize()).isEqualTo(27);
    }

    @Test
    void theDefaultLayoutStillOpensFiftyFourSlots() {
        openAndReturn(GuiLayout.paginatedDefault(Material.RED_BED));

        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(54);
    }

    @Test
    void theTitleStaysCatalogDrivenAcrossLayouts() {
        PaginatedGui gui = openAndReturn(layout(3));

        assertThat(gui.title()).isEqualTo(Component.text(TITLE));
    }

    private PaginatedGui openAndReturn(GuiLayout layout) {
        HomeMenuView view = new HomeMenuView(new KeyMessages(), new SyncScheduler(), teleportHome(), layout);
        PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());
        view.open(player, viewer, homes());
        return (PaginatedGui) player.getOpenInventory().getTopInventory().getHolder();
    }

    private static GuiLayout layout(int rows) {
        return new GuiLayout(rows, Material.RED_BED, Material.ARROW, rows * 9 - 6, rows * 9 - 4, List.of());
    }

    private static List<Home> homes() {
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        PlayerRef owner = new PlayerRef(UUID.randomUUID(), "Owner");
        return List.of(Home.create(owner, HomeName.of("base"), Position.of(world, 0, 64, 0), Instant.now()));
    }

    private TeleportHome teleportHome() {
        Messages messages = new KeyMessages();
        HomeNotifier notifier = new HomeNotifier(messages, new NoSink());
        HomeRepository repository = new FakeRepository();
        MainHomePreference mainHomes = new NoMainHome();
        HomeTeleporter teleporter = (who, home) -> {};
        return new TeleportHome(repository, mainHomes, teleporter, notifier);
    }

    private static final class FakeRepository implements HomeRepository {
        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, homes());
        }

        @Override
        public int count(PlayerRef owner) {
            return homes().size();
        }

        @Override
        public void save(Home home) {}

        @Override
        public void rename(PlayerRef owner, HomeName from, HomeName to) {}

        @Override
        public void delete(PlayerRef owner, HomeName name) {}
    }

    private static final class NoMainHome implements MainHomePreference {
        @Override
        public Optional<HomeName> mainHomeOf(PlayerRef owner) {
            return Optional.empty();
        }

        @Override
        public void setMainHome(PlayerRef owner, HomeName name) {}

        @Override
        public void clear(PlayerRef owner) {}
    }

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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
    }
}

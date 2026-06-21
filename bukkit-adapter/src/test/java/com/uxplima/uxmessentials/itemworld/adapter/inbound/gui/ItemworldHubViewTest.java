package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.application.PurgePolicy;
import com.uxplima.uxmessentials.itemworld.application.port.ItemworldAudit;
import com.uxplima.uxmessentials.itemworld.domain.MobSpec;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the itemworld utilities hub. The launcher renders one button per action at its conf slot;
 * clicking the ender-chest button opens that workstation (the {@code MenuType}-backed stations need a titled view
 * the mock server cannot create, so the ender chest — a plain inventory — stands in for the workstation group);
 * clicking a time button sets the world time through the same path {@code /day} uses, and a weather button sets the
 * weather like {@code /weather}. The scheduler runs every hop inline so the entity- and global-bound actions are
 * observable synchronously.
 */
class ItemworldHubViewTest {

    private static final int ENDERCHEST_SLOT = 7;
    private static final int TIME_NIGHT_SLOT = 11;
    private static final int WEATHER_RAIN_SLOT = 13;
    private static final int BACK_SLOT = 22;

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private World world;
    private GuiText guiText;
    private Scheduler scheduler;
    private MutableConfig config;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        config = new MutableConfig();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void rendersLauncherButtonsAtTheirConfSlots() throws Exception {
        view(new AllowAllPermissions()).open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(SimpleGui.class);
        assertThat(inv.getItem(ENDERCHEST_SLOT).getType()).isEqualTo(Material.ENDER_CHEST);
        assertThat(inv.getItem(TIME_NIGHT_SLOT).getType()).isEqualTo(Material.CLOCK);
        assertThat(inv.getItem(WEATHER_RAIN_SLOT).getType()).isEqualTo(Material.WATER_BUCKET);
        assertThat(inv.getItem(BACK_SLOT).getType()).isEqualTo(Material.ARROW);
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.CRAFTING_TABLE); // workbench at slot 0
    }

    @Test
    void clickingTheEnderchestButtonOpensThatWorkstation() throws Exception {
        view(new AllowAllPermissions()).open(player, viewer);

        fireClick(ENDERCHEST_SLOT, ClickType.LEFT);

        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.ENDER_CHEST);
    }

    @Test
    void clickingTheTimeButtonSetsTheWorldTime() throws Exception {
        world.setTime(6000L); // midday
        view(new AllowAllPermissions()).open(player, viewer);

        fireClick(TIME_NIGHT_SLOT, ClickType.LEFT);

        assertThat(world.getTime()).isEqualTo(13_000L); // /night through the same path
    }

    @Test
    void clickingTheWeatherButtonRunsTheUseCase() throws Exception {
        world.setStorm(false);
        view(new AllowAllPermissions()).open(player, viewer);

        fireClick(WEATHER_RAIN_SLOT, ClickType.LEFT);

        assertThat(world.hasStorm()).isTrue(); // /weather rain through the same applier
    }

    @Test
    void aButtonTheViewerMayNotUseIsNotDrawn() throws Exception {
        view(new DenyAllPermissions()).open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        // No permission for any action, so the world-action slots stay filler, not buttons.
        assertThat(inv.getItem(TIME_NIGHT_SLOT).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
        assertThat(inv.getItem(ENDERCHEST_SLOT).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemworldHubView view(Permissions permissions) throws Exception {
        layout();
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        ItemworldServices services = new ItemworldServices(
                kernel(permissions), new NoopAudit(), ItemworldConfig.from(config), GuiLayout.storageDefault(6));
        return new ItemworldHubView(
                guiText, scheduler, permissions, services, new PurgePolicy(ItemworldConfig.from(config)), layouts);
    }

    private void layout() throws Exception {
        Path file = dir.resolve("modules").resolve("itemworld").resolve("gui").resolve("itemworld-hub.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 3
                filler-material = "BLACK_STAINED_GLASS_PANE"
                slots {
                  workbench = 0
                  anvil = 1
                  cartography = 2
                  grindstone = 3
                  loom = 4
                  smithingtable = 5
                  stonecutter = 6
                  enderchest = 7
                  time-day = 10
                  time-night = 11
                  weather-clear = 12
                  weather-rain = 13
                  clear-drops = 15
                  clear-mobs = 16
                  back = 22
                }
                materials {
                  workbench = "CRAFTING_TABLE"
                  anvil = "ANVIL"
                  cartography = "CARTOGRAPHY_TABLE"
                  grindstone = "GRINDSTONE"
                  loom = "LOOM"
                  smithingtable = "SMITHING_TABLE"
                  stonecutter = "STONECUTTER"
                  enderchest = "ENDER_CHEST"
                  time-day = "SUNFLOWER"
                  time-night = "CLOCK"
                  weather-clear = "YELLOW_STAINED_GLASS"
                  weather-rain = "WATER_BUCKET"
                  clear-drops = "HOPPER"
                  clear-mobs = "IRON_SWORD"
                  back = "ARROW"
                }
                """);
    }

    private KernelPorts kernel(Permissions permissions) {
        return new KernelPorts(
                scheduler,
                permissions,
                new NoCooldowns(),
                new NoWarmups(),
                new KeyMessages(),
                new NoopSink(),
                new NoPlayerLookup(),
                new NoWorldLookup(),
                new NoPlayerLocator(),
                new NoEvents(),
                NOOP);
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static final class MutableConfig implements ConfigStore {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who,
                QuotaFamily family,
                com.uxplima.uxmessentials.shared.domain.@Nullable WorldRef world,
                long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class DenyAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who,
                QuotaFamily family,
                com.uxplima.uxmessentials.shared.domain.@Nullable WorldRef world,
                long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoCooldowns implements Cooldowns {
        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                check(PlayerRef who, CooldownKind kind) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                checkLabel(PlayerRef who, String label) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class NoWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new Warmups.CompletedWarmup(who);
        }
    }

    private static final class NoPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    private static final class NoWorldLookup implements WorldLookup {
        @Override
        public Optional<com.uxplima.uxmessentials.shared.domain.WorldRef> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<com.uxplima.uxmessentials.shared.domain.WorldRef> findByUid(UUID uid) {
            return Optional.empty();
        }
    }

    private static final class NoPlayerLocator implements PlayerLocator {
        @Override
        public Optional<Position> locate(PlayerRef who) {
            return Optional.empty();
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoopAudit implements ItemworldAudit {
        @Override
        public void gave(PlayerRef actor, PlayerRef target, String itemKey, int amount) {}

        @Override
        public void spawnedMob(PlayerRef actor, MobSpec spec, int spawned) {}

        @Override
        public void retypedSpawner(PlayerRef actor, String mobType) {}

        @Override
        public void killed(PlayerRef actor, String target) {}

        @Override
        public void butchered(PlayerRef actor, PurgeSelection selection, int removed) {}

        @Override
        public void killedAll(PlayerRef actor, PurgeSelection selection, int removed) {}

        @Override
        public void removed(PlayerRef actor, PurgeSelection selection, int removed) {}

        @Override
        public void struckLightning(PlayerRef actor, Optional<PlayerRef> target) {}

        @Override
        public void launchedFireball(PlayerRef actor) {}

        @Override
        public void firedKittycannon(PlayerRef actor) {}

        @Override
        public void threwAntioch(PlayerRef actor) {}

        @Override
        public void firedBeezooka(PlayerRef actor) {}

        @Override
        public void brokeBlock(PlayerRef actor, String blockType) {}

        @Override
        public void grewTree(PlayerRef actor, String type) {}

        @Override
        public void nuked(PlayerRef actor, Optional<PlayerRef> target) {}
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

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

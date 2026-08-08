package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.Engine;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.FakeRepository;
import com.uxplima.uxmessentials.shared.menu.WorldEditorTestSupport.RecordingEvents;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldGridMenu;
import com.uxplima.uxmessentials.worlds.application.SetWorldProperty;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The shared property-grid golden test: the engine-rendered grid must draw the property buttons the original
 * {@code WorldPropertyGridView} drew for each property set (the rules set leads with PvP / DIAMOND_SWORD, the access
 * set with restricted-access / IRON_DOOR), and a click must cycle the clicked property through the cycle use case and
 * re-render with the new value. The behaviour is proved through the engine's own {@code MenuListener}: a left click on
 * the first rules cell flips PvP true -> false and re-opens the grid showing the new value, and a left click on the
 * first access cell flips restricted-access false -> true the same way. Each cycle runs the real
 * {@link SetWorldProperty} use case (the recording repository captures the persisted value) and re-renders, so a lost
 * cycle, a wrong property, or a stale re-render all surface as a failure.
 */
class WorldGridGoldenTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeRepository repository;
    private RecordingEvents events;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Admin");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new WorldEditorTestSupport.TokenMessages(
                com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey.PROPERTY_VALUE_LORE,
                "world_grid_value"));
        scheduler = new WorldEditorTestSupport.SyncScheduler();
        repository = new FakeRepository();
        events = new RecordingEvents();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheRulesSetLeadingWithPvp() {
        repository.seed("alpha", WorldEnvironment.NORMAL);

        Inventory grid = open(true);

        assertThat(grid.getItem(0)).isNotNull();
        assertThat(grid.getItem(0).getType()).isEqualTo(Material.DIAMOND_SWORD);
        // PvP defaults true; the value-lore surfaces the current encoded value through the world_grid_value token.
        assertThat(valueLore(grid.getItem(0))).contains("true");
    }

    @Test
    void engineRendersTheAccessSetLeadingWithRestrictedAccess() {
        repository.seed("alpha", WorldEnvironment.NORMAL);

        Inventory grid = open(false);

        assertThat(grid.getItem(0)).isNotNull();
        assertThat(grid.getItem(0).getType()).isEqualTo(Material.IRON_DOOR);
        assertThat(valueLore(grid.getItem(0))).contains("false");
    }

    @Test
    void cyclingARulesPropertyRunsTheUseCaseAndReRendersWithTheNewValue() {
        repository.seed("alpha", WorldEnvironment.NORMAL);
        open(true);
        // Content slot 0 is PvP (default true); a left click cycles it forward to false, persists it, and re-renders.
        WorldEditorTestSupport.fireClick(server, player, 0, ClickType.LEFT);

        assertThat(savedValue(WorldProperties.PVP)).isEqualTo("false");
        Inventory grid = player.getOpenInventory().getTopInventory();
        assertThat(grid.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(valueLore(grid.getItem(0))).contains("false");
    }

    @Test
    void cyclingAnAccessPropertyRunsTheUseCaseAndReRendersWithTheNewValue() {
        repository.seed("alpha", WorldEnvironment.NORMAL);
        open(false);
        // Content slot 0 is restricted-access (default false); a left click cycles it forward to true and re-renders.
        WorldEditorTestSupport.fireClick(server, player, 0, ClickType.LEFT);

        assertThat(savedValue(WorldProperties.ACCESS_RESTRICTED)).isEqualTo("true");
        Inventory grid = player.getOpenInventory().getTopInventory();
        assertThat(grid.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(valueLore(grid.getItem(0))).contains("true");
    }

    /** Wire the grid over the engine and open it for "alpha" showing the rules set when {@code rules} is true. */
    private Inventory open(boolean rules) {
        Engine eng = WorldEditorTestSupport.engine(server, plugin, guiText, scheduler);
        WorldGridMenu gridMenu = new WorldGridMenu(eng.menus(), scheduler, repository, setProperty());
        // The back button needs a bound hub; this test never clicks it, so a real (unregistered) hub suffices.
        gridMenu.bind(new com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldMainMenu(
                eng.menus(), scheduler, repository, new WorldEditorTestSupport.FakeEngine(), (p, v) -> {}));
        gridMenu.register(eng.bindings(), dataFolder, WorldEditorTestSupport.NOOP);
        gridMenu.open(player, viewer, WorldName.of("alpha"), rules);
        return player.getOpenInventory().getTopInventory();
    }

    private SetWorldProperty setProperty() {
        return new SetWorldProperty(
                repository,
                new Notifier(new WorldEditorTestSupport.KeyMessages(), new WorldEditorTestSupport.SilentSink()),
                events,
                scheduler);
    }

    /** The encoded value the use case persisted for {@code property} on "alpha", read from the recording repository. */
    private String savedValue(com.uxplima.uxmessentials.worlds.domain.WorldProperty<?> property) {
        ManagedWorld world = repository.find(WorldName.of("alpha")).orElseThrow();
        WorldSettings settings = world.settings();
        return encode(settings, property);
    }

    private static <T> String encode(
            WorldSettings settings, com.uxplima.uxmessentials.worlds.domain.WorldProperty<T> property) {
        return property.encode(settings.get(property));
    }

    /** The plain text of the item's value-lore line (the world_grid_value token the catalog surfaces). */
    private static String valueLore(ItemStack item) {
        List<Component> lore =
                java.util.Objects.requireNonNull(item.getItemMeta()).lore();
        if (lore == null || lore.isEmpty()) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(lore.get(0));
    }
}

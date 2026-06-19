package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.worlds.adapter.WorldsServices;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.DeleteWorld;
import com.uxplima.uxmessentials.worlds.application.ImportWorld;
import com.uxplima.uxmessentials.worlds.application.ListWorlds;
import com.uxplima.uxmessentials.worlds.application.LoadWorld;
import com.uxplima.uxmessentials.worlds.application.PregenWorld;
import com.uxplima.uxmessentials.worlds.application.SetGamerule;
import com.uxplima.uxmessentials.worlds.application.SetWorldAlias;
import com.uxplima.uxmessentials.worlds.application.SetWorldProperty;
import com.uxplima.uxmessentials.worlds.application.SetWorldSpawn;
import com.uxplima.uxmessentials.worlds.application.UnloadWorld;
import com.uxplima.uxmessentials.worlds.application.UnregisterWorld;
import com.uxplima.uxmessentials.worlds.application.WorldInfo;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the single click-routing listener that ties the four world-editor views together. The
 * scheduler is synchronous so the entity-bound view opens run inline; the repository is a seeded fake and a fake
 * {@link WorldEngine} answers loaded-state queries. The three mutating use cases are Mockito mocks so a click can be
 * asserted to route into exactly one of them with the expected arguments.
 *
 * <p>Every case is exercised live: the listener is registered with the plugin manager and a real
 * {@link InventoryClickEvent} is fired against the open editor window, so the whole holder-recognition,
 * cancel, dispatch, and optimistic-update chain runs end to end. MockBukkit resolves {@code getInventory()} and
 * {@code getRawSlot()} for a top-container click, which is all the listener reads.
 */
class WorldEditorListenerTest {

    private static final WorldName WORLD = WorldName.of("world");

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock viewer;
    private FakeWorldRepository repository;
    private FakeWorldEngine engine;
    private SetWorldProperty setWorldProperty;
    private LoadWorld loadWorld;
    private UnloadWorld unloadWorld;
    private WorldListView listView;
    private WorldMainView mainView;
    private WorldGenerationView generationView;
    private WorldPropertyGridView gridView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        viewer = server.addPlayer("Staff");
        repository = new FakeWorldRepository();
        // pvp defaults to true; seeding it explicitly true makes the first left-click cycle to false.
        repository.add(world(WORLD, WorldSettings.defaults().with(WorldProperties.PVP, true)));
        engine = new FakeWorldEngine();

        WorldEditorText text = new WorldEditorText(new KeyMessages(), MiniMessage.miniMessage());
        Scheduler scheduler = new SyncScheduler();
        GuiLayout layout = GuiLayout.paginatedDefault(Material.PAPER);
        listView = new WorldListView(text, repository, engine, scheduler, layout);
        mainView = new WorldMainView(text, repository, engine, scheduler, layout);
        generationView = new WorldGenerationView(text, repository, scheduler, layout);
        gridView = new WorldPropertyGridView(text, repository, scheduler, layout);

        setWorldProperty = mock(SetWorldProperty.class);
        when(setWorldProperty.set(any(), any(), any(), any())).thenReturn(Result.ok());
        loadWorld = mock(LoadWorld.class);
        when(loadWorld.load(any(), any())).thenReturn(Result.ok());
        unloadWorld = mock(UnloadWorld.class);
        when(unloadWorld.unload(any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(Result.ok());

        WorldsServices services = services();
        WorldEditorListener listener =
                new WorldEditorListener(listView, mainView, generationView, gridView, services, repository, engine);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void leftClickingThePvpButtonOnRulesCyclesItToFalseAndUpdatesTheButton() {
        gridView.open(viewer, ref(viewer), WORLD, WorldEditorScreen.RULES, WorldPropertyGridView.RULES_PROPERTIES);
        int pvpSlot = firstContentSlot();

        fireClick(pvpSlot, ClickType.LEFT);

        verify(setWorldProperty).set(ref(viewer), WORLD, "pvp", "false");
        ItemStack pvpButton = viewer.getOpenInventory().getTopInventory().getItem(pvpSlot);
        assertThat(loreOf(pvpButton)).contains("false");
    }

    @Test
    void shiftClickingASetPortalLinkOnAccessClearsItBackToVanilla() {
        repository.add(world(WORLD, WorldSettings.defaults().with(WorldProperties.PORTAL_NETHER_LINK, "thenether")));
        gridView.open(viewer, ref(viewer), WORLD, WorldEditorScreen.ACCESS, WorldPropertyGridView.ACCESS_PROPERTIES);
        int portalNetherSlot =
                contentSlot(WorldPropertyGridView.ACCESS_PROPERTIES.indexOf(WorldProperties.PORTAL_NETHER_LINK));

        fireClick(portalNetherSlot, ClickType.SHIFT_LEFT);

        verify(setWorldProperty).set(ref(viewer), WORLD, "portal-nether-link", "");
        ItemStack portalButton = viewer.getOpenInventory().getTopInventory().getItem(portalNetherSlot);
        assertThat(loreOf(portalButton)).doesNotContain("thenether");
    }

    @Test
    void clickingTheRulesNavButtonOnMainOpensTheRulesGrid() {
        mainView.open(viewer, ref(viewer), WORLD);

        fireClick(11, ClickType.LEFT); // the MAIN hub's rules nav slot

        InventoryHolder holder = viewer.getOpenInventory().getTopInventory().getHolder();
        assertThat(holder).isInstanceOf(WorldEditorHolder.class);
        assertThat(((WorldEditorHolder) holder).screen()).isEqualTo(WorldEditorScreen.RULES);
    }

    @Test
    void clickingBackOnAGridReturnsToTheMainHub() {
        gridView.open(viewer, ref(viewer), WORLD, WorldEditorScreen.RULES, WorldPropertyGridView.RULES_PROPERTIES);

        fireClick(WorldPropertyGridView.BACK_SLOT, ClickType.LEFT);

        InventoryHolder holder = viewer.getOpenInventory().getTopInventory().getHolder();
        assertThat(((WorldEditorHolder) holder).screen()).isEqualTo(WorldEditorScreen.MAIN);
    }

    @Test
    void everyClickInAnEditorWindowIsCancelled() {
        gridView.open(viewer, ref(viewer), WORLD, WorldEditorScreen.RULES, WorldPropertyGridView.RULES_PROPERTIES);

        InventoryClickEvent event = clickEvent(firstContentSlot(), ClickType.LEFT);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void aClickInANonEditorInventoryIsIgnored() {
        Inventory plain = server.createInventory(null, 27);
        viewer.openInventory(plain);

        InventoryClickEvent event = clickEvent(0, ClickType.LEFT);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
        verifyNoInteractions(setWorldProperty, loadWorld, unloadWorld);
    }

    private void fireClick(int slot, ClickType type) {
        server.getPluginManager().callEvent(clickEvent(slot, type));
    }

    private InventoryClickEvent clickEvent(int slot, ClickType type) {
        InventoryView view = viewer.getOpenInventory();
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
    }

    private int firstContentSlot() {
        return contentSlot(0);
    }

    private int contentSlot(int index) {
        return GuiLayout.paginatedDefault(Material.PAPER)
                .explicitContentSlots()
                .map(slots -> slots.get(index))
                .orElse(index);
    }

    private static String loreOf(ItemStack item) {
        if (item == null || item.lore() == null) {
            return "";
        }
        return item.lore().stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                .reduce("", (a, b) -> a + " " + b);
    }

    private WorldsServices services() {
        return new WorldsServices(
                mock(CreateWorld.class),
                mock(ImportWorld.class),
                loadWorld,
                unloadWorld,
                mock(UnregisterWorld.class),
                mock(DeleteWorld.class),
                mock(ListWorlds.class),
                mock(WorldInfo.class),
                setWorldProperty,
                mock(SetGamerule.class),
                mock(SetWorldSpawn.class),
                mock(SetWorldAlias.class),
                mock(PregenWorld.class),
                mock(GameRuleCatalog.class),
                repository,
                new SyncScheduler(),
                Set::of,
                mock(WorldTeleportService.class),
                listView,
                mainView);
    }

    private static ManagedWorld world(WorldName name, WorldSettings settings) {
        WorldSpec spec = new WorldSpec(
                WorldEnvironment.NORMAL,
                WorldGenType.NORMAL,
                Optional.empty(),
                Optional.empty(),
                true,
                Optional.empty());
        return ManagedWorld.created(name, spec, true, Optional.empty(), Instant.EPOCH)
                .withSettings(settings);
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private static final class FakeWorldRepository implements WorldRepository {
        private final Map<String, ManagedWorld> store = new LinkedHashMap<>();

        void add(ManagedWorld world) {
            store.put(world.name().value(), world);
        }

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(store.get(name.value()));
        }

        @Override
        public List<ManagedWorld> all() {
            return new ArrayList<>(store.values());
        }

        @Override
        public boolean exists(WorldName name) {
            return store.containsKey(name.value());
        }

        @Override
        public void save(ManagedWorld world) {
            store.put(world.name().value(), world);
        }

        @Override
        public void delete(WorldName name) {
            store.remove(name.value());
        }
    }

    private static final class FakeWorldEngine implements WorldEngine {
        private final Set<WorldName> loaded = new java.util.HashSet<>();

        @Override
        public Result<com.uxplima.uxmessentials.shared.domain.Unit, WorldError> create(ManagedWorld world) {
            return Result.ok();
        }

        @Override
        public Result<com.uxplima.uxmessentials.shared.domain.Unit, WorldError> load(ManagedWorld world) {
            loaded.add(world.name());
            return Result.ok();
        }

        @Override
        public Result<com.uxplima.uxmessentials.shared.domain.Unit, WorldError> unload(WorldName name, boolean save) {
            loaded.remove(name);
            return Result.ok();
        }

        @Override
        public Result<com.uxplima.uxmessentials.shared.domain.Unit, WorldError> deleteFiles(WorldName name) {
            return Result.ok();
        }

        @Override
        public Optional<DetectedWorld> scanFolder(WorldName name) {
            return Optional.empty();
        }

        @Override
        public boolean exists(WorldName name) {
            return true;
        }

        @Override
        public boolean isLoaded(WorldName name) {
            return loaded.contains(name);
        }

        @Override
        public Set<WorldName> loadedWorldNames() {
            return Set.copyOf(loaded);
        }

        @Override
        public Optional<WorldName> defaultWorldName() {
            return Optional.empty();
        }

        @Override
        public Optional<java.util.UUID> uidOf(WorldName name) {
            return Optional.empty();
        }

        @Override
        public int playerCount(WorldName name) {
            return 0;
        }

        @Override
        public Optional<Position> spawnPoint(WorldName name) {
            return Optional.empty();
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            String resolved = key.key();
            if (placeholders.containsKey("value")) {
                resolved = resolved + " " + placeholders.get("value");
            }
            return resolved;
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

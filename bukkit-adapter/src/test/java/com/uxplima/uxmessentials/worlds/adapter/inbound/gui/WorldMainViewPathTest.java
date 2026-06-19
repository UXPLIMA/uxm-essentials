package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the per-world editor screens. Opening {@link WorldMainView} tags the window with a
 * {@link WorldEditorScreen#MAIN} holder carrying the edited world, places the summary, navigation, toggle, and
 * back items at their fixed slots, and {@link WorldMainView#actionAt} maps each button slot to its {@link
 * MainAction}; the load/unload toggle swaps material and label with the engine's loaded state. Opening {@link
 * WorldGenerationView} tags a {@link WorldEditorScreen#GENERATION} holder, fills the four read-only info slots and
 * the back button, and {@link WorldGenerationView#isBack} recognises only the back slot. The scheduler is a
 * synchronous double so the entity-bound open runs inline; the repository and engine are seeded fakes.
 */
class WorldMainViewPathTest {

    private static final int SUMMARY_SLOT = 4;
    private static final int RULES_SLOT = 11;
    private static final int GENERATION_SLOT = 13;
    private static final int ACCESS_SLOT = 15;
    private static final int BACK_SLOT = 18;
    private static final int TOGGLE_SLOT = 22;

    private static final int GEN_ENV_SLOT = 10;
    private static final int GEN_TYPE_SLOT = 12;
    private static final int GEN_SEED_SLOT = 14;
    private static final int GEN_GENERATOR_SLOT = 16;
    private static final int GEN_BACK_SLOT = 22;

    private ServerMock server;
    private PlayerMock viewer;
    private FakeWorldRepository repository;
    private FakeWorldEngine engine;
    private WorldEditorText text;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Staff");
        repository = new FakeWorldRepository();
        engine = new FakeWorldEngine();
        repository.add(world("world", WorldEnvironment.NORMAL));
        repository.add(world("hell", WorldEnvironment.NETHER));
        engine.loaded.add("world");
        text = new WorldEditorText(new KeyMessages(), MiniMessage.miniMessage());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void opensAMainScreenHolderForTheEditedWorld() {
        mainView().open(viewer, ref(viewer), WorldName.of("world"));

        InventoryHolder holder = viewer.getOpenInventory().getTopInventory().getHolder();
        assertThat(holder).isInstanceOf(WorldEditorHolder.class);
        WorldEditorHolder editor = (WorldEditorHolder) holder;
        assertThat(editor.screen()).isEqualTo(WorldEditorScreen.MAIN);
        assertThat(editor.world()).isEqualTo(WorldName.of("world"));
    }

    @Test
    void placesSummaryNavigationToggleAndBackItems() {
        mainView().open(viewer, ref(viewer), WorldName.of("world"));
        Inventory inventory = viewer.getOpenInventory().getTopInventory();

        for (int slot : List.of(SUMMARY_SLOT, RULES_SLOT, GENERATION_SLOT, ACCESS_SLOT, TOGGLE_SLOT, BACK_SLOT)) {
            assertThat(present(inventory, slot)).as("slot %d", slot).isTrue();
        }
    }

    @Test
    void mapsButtonSlotsToActionsAndEmptySlotsToNothing() {
        WorldMainView view = mainView();

        assertThat(view.actionAt(RULES_SLOT)).contains(MainAction.RULES);
        assertThat(view.actionAt(GENERATION_SLOT)).contains(MainAction.GENERATION);
        assertThat(view.actionAt(ACCESS_SLOT)).contains(MainAction.ACCESS);
        assertThat(view.actionAt(TOGGLE_SLOT)).contains(MainAction.TOGGLE_LOAD);
        assertThat(view.actionAt(BACK_SLOT)).contains(MainAction.BACK);
        assertThat(view.actionAt(0)).isEmpty();
    }

    @Test
    void theToggleItemFollowsTheLoadedState() {
        mainView().open(viewer, ref(viewer), WorldName.of("world"));
        ItemStack loadedToggle = viewer.getOpenInventory().getTopInventory().getItem(TOGGLE_SLOT);
        assertThat(loadedToggle).isNotNull();
        assertThat(loadedToggle.getType()).isEqualTo(Material.LIME_DYE);

        engine.loaded.remove("world");
        mainView().open(viewer, ref(viewer), WorldName.of("world"));
        ItemStack unloadedToggle = viewer.getOpenInventory().getTopInventory().getItem(TOGGLE_SLOT);
        assertThat(unloadedToggle).isNotNull();
        assertThat(unloadedToggle.getType()).isEqualTo(Material.GRAY_DYE);
    }

    @Test
    void opensAReadOnlyGenerationScreen() {
        WorldGenerationView view = new WorldGenerationView(text, repository, new SyncScheduler(), threeRowLayout());
        view.open(viewer, ref(viewer), WorldName.of("world"));
        Inventory inventory = viewer.getOpenInventory().getTopInventory();

        InventoryHolder holder = inventory.getHolder();
        assertThat(holder).isInstanceOf(WorldEditorHolder.class);
        assertThat(((WorldEditorHolder) holder).screen()).isEqualTo(WorldEditorScreen.GENERATION);
        for (int slot : List.of(GEN_ENV_SLOT, GEN_TYPE_SLOT, GEN_SEED_SLOT, GEN_GENERATOR_SLOT, GEN_BACK_SLOT)) {
            assertThat(present(inventory, slot)).as("slot %d", slot).isTrue();
        }
        assertThat(view.isBack(GEN_BACK_SLOT)).isTrue();
        assertThat(view.isBack(GEN_ENV_SLOT)).isFalse();
    }

    private WorldMainView mainView() {
        return new WorldMainView(text, repository, engine, new SyncScheduler(), threeRowLayout());
    }

    private static GuiLayout threeRowLayout() {
        return new GuiLayout(3, Material.GRASS_BLOCK, Material.ARROW, 0, 1, List.of());
    }

    private static boolean present(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        return item != null && !item.getType().isAir();
    }

    private static ManagedWorld world(String name, WorldEnvironment environment) {
        WorldSpec spec = new WorldSpec(
                environment, WorldGenType.NORMAL, Optional.empty(), Optional.empty(), true, Optional.empty());
        return ManagedWorld.created(WorldName.of(name), spec, true, Optional.empty(), Instant.EPOCH);
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
        private final Set<String> loaded = new java.util.HashSet<>();

        @Override
        public Result<Unit, WorldError> create(ManagedWorld world) {
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> load(ManagedWorld world) {
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> unload(WorldName name, boolean save) {
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> deleteFiles(WorldName name) {
            return Result.ok();
        }

        @Override
        public Optional<DetectedWorld> scanFolder(WorldName name) {
            return Optional.empty();
        }

        @Override
        public boolean exists(WorldName name) {
            return loaded.contains(name.value());
        }

        @Override
        public boolean isLoaded(WorldName name) {
            return loaded.contains(name.value());
        }

        @Override
        public Set<WorldName> loadedWorldNames() {
            return Set.of();
        }

        @Override
        public Optional<WorldName> defaultWorldName() {
            return Optional.of(WorldName.of("world"));
        }

        @Override
        public Optional<UUID> uidOf(WorldName name) {
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

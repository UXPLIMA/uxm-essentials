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
 * MockBukkit coverage of the {@link WorldEditorScreen#LIST} world-picker: opening the view places one icon per
 * managed world at the layout's content slots, each icon's material reflecting the world's environment, and
 * {@link WorldListView#worldAt} maps a clicked content slot back to the world drawn there. The scheduler is a
 * synchronous double so the entity-bound open runs inline; the repository and engine are seeded fakes.
 */
class WorldListViewPathTest {

    private ServerMock server;
    private PlayerMock viewer;
    private FakeWorldRepository repository;
    private FakeWorldEngine engine;
    private WorldListView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Staff");
        repository = new FakeWorldRepository();
        engine = new FakeWorldEngine();
        repository.add(world("world", WorldEnvironment.NORMAL));
        repository.add(world("hell", WorldEnvironment.NETHER));
        repository.add(world("void", WorldEnvironment.THE_END));
        engine.loaded.add("world");
        WorldEditorText text = new WorldEditorText(new KeyMessages(), MiniMessage.miniMessage());
        view = new WorldListView(
                text, repository, engine, new SyncScheduler(), GuiLayout.paginatedDefault(Material.GRASS_BLOCK));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void opensAListScreenHolder() {
        view.open(viewer, ref(viewer), 0);

        InventoryHolder holder = viewer.getOpenInventory().getTopInventory().getHolder();
        assertThat(holder).isInstanceOf(WorldEditorHolder.class);
        assertThat(((WorldEditorHolder) holder).screen()).isEqualTo(WorldEditorScreen.LIST);
        assertThat(((WorldEditorHolder) holder).page()).isZero();
    }

    @Test
    void placesOneIconPerManagedWorldKeyedByEnvironment() {
        view.open(viewer, ref(viewer), 0);
        Inventory inventory = viewer.getOpenInventory().getTopInventory();
        List<Integer> contentSlots = view.layout().explicitContentSlots().orElseGet(() -> {
            List<Integer> defaults = new ArrayList<>();
            int limit = (view.layout().rows() - 1) * 9;
            for (int i = 0; i < limit; i++) {
                defaults.add(i);
            }
            return defaults;
        });

        assertThat(inventory.getItem(contentSlots.get(0)).getType()).isEqualTo(Material.GRASS_BLOCK);
        assertThat(inventory.getItem(contentSlots.get(1)).getType()).isEqualTo(Material.NETHERRACK);
        assertThat(inventory.getItem(contentSlots.get(2)).getType()).isEqualTo(Material.END_STONE);
        long placed = contentSlots.stream()
                .map(inventory::getItem)
                .filter(item -> item != null && !item.getType().isAir())
                .count();
        assertThat(placed).isEqualTo(3);
    }

    @Test
    void worldAtMapsTheFirstContentSlotToTheFirstWorld() {
        int firstContentSlot =
                view.layout().explicitContentSlots().map(slots -> slots.get(0)).orElse(0);

        assertThat(view.worldAt(0, firstContentSlot)).contains(WorldName.of("world"));
        assertThat(view.worldAt(0, view.layout().prevSlot())).isEmpty();
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

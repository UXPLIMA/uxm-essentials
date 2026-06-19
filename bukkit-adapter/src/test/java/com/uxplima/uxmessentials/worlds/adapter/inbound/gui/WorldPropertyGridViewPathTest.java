package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
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
 * MockBukkit coverage of the shared property-grid screen that drives both {@link WorldEditorScreen#RULES} and
 * {@link WorldEditorScreen#ACCESS}: opening the view places one button per property at the layout's content slots,
 * each button's value-lore reporting the world's current encoded setting, and {@link WorldPropertyGridView#propertyAt}
 * maps a clicked content slot back to the property drawn there. The scheduler is a synchronous double so the
 * entity-bound open runs inline; the repository is a seeded fake.
 */
class WorldPropertyGridViewPathTest {

    private ServerMock server;
    private PlayerMock viewer;
    private FakeWorldRepository repository;
    private WorldPropertyGridView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Staff");
        repository = new FakeWorldRepository();
        WorldSettings settings = WorldSettings.defaults().with(WorldProperties.PVP, false);
        repository.add(world("world", settings));
        WorldEditorText text = new WorldEditorText(new KeyMessages(), MiniMessage.miniMessage());
        view = new WorldPropertyGridView(
                text, repository, new SyncScheduler(), GuiLayout.paginatedDefault(org.bukkit.Material.PAPER));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void opensARulesScreenHolderWithOneButtonPerRulesProperty() {
        view.open(
                viewer,
                ref(viewer),
                WorldName.of("world"),
                WorldEditorScreen.RULES,
                WorldPropertyGridView.RULES_PROPERTIES);

        InventoryHolder holder = viewer.getOpenInventory().getTopInventory().getHolder();
        assertThat(holder).isInstanceOf(WorldEditorHolder.class);
        assertThat(((WorldEditorHolder) holder).screen()).isEqualTo(WorldEditorScreen.RULES);
        assertThat(((WorldEditorHolder) holder).world()).isEqualTo(WorldName.of("world"));

        Inventory inventory = viewer.getOpenInventory().getTopInventory();
        List<Integer> contentSlots = contentSlots();
        long placed = contentSlots.stream()
                .limit(WorldPropertyGridView.RULES_PROPERTIES.size())
                .map(inventory::getItem)
                .filter(item -> item != null && !item.getType().isAir())
                .count();
        assertThat(placed).isEqualTo(WorldPropertyGridView.RULES_PROPERTIES.size());
    }

    @Test
    void propertyAtMapsTheFirstContentSlotToTheFirstRulesProperty() {
        int firstContentSlot = contentSlots().get(0);

        assertThat(view.propertyAt(firstContentSlot, WorldPropertyGridView.RULES_PROPERTIES))
                .contains(WorldProperties.PVP);
        assertThat(view.isBack(WorldPropertyGridView.BACK_SLOT)).isTrue();
        assertThat(view.isBack(firstContentSlot)).isFalse();
    }

    @Test
    void opensAnAccessScreenHolderMappingTheFirstSlotToAccessRestricted() {
        view.open(
                viewer,
                ref(viewer),
                WorldName.of("world"),
                WorldEditorScreen.ACCESS,
                WorldPropertyGridView.ACCESS_PROPERTIES);

        InventoryHolder holder = viewer.getOpenInventory().getTopInventory().getHolder();
        assertThat(((WorldEditorHolder) holder).screen()).isEqualTo(WorldEditorScreen.ACCESS);

        int firstContentSlot = contentSlots().get(0);
        assertThat(view.propertyAt(firstContentSlot, WorldPropertyGridView.ACCESS_PROPERTIES))
                .contains(WorldProperties.ACCESS_RESTRICTED);
    }

    @Test
    void valueLoreReflectsTheWorldsCurrentSetting() {
        view.open(
                viewer,
                ref(viewer),
                WorldName.of("world"),
                WorldEditorScreen.RULES,
                WorldPropertyGridView.RULES_PROPERTIES);

        Inventory inventory = viewer.getOpenInventory().getTopInventory();
        ItemStack pvpButton = inventory.getItem(contentSlots().get(0));
        assertThat(pvpButton).isNotNull();
        String lore = pvpButton.lore() == null
                ? ""
                : pvpButton.lore().stream()
                        .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                        .reduce("", (a, b) -> a + " " + b);
        assertThat(lore).contains("false");
    }

    private List<Integer> contentSlots() {
        return view.layout().explicitContentSlots().orElseGet(() -> {
            List<Integer> defaults = new ArrayList<>();
            int limit = (view.layout().rows() - 1) * 9;
            for (int i = 0; i < limit; i++) {
                defaults.add(i);
            }
            return defaults;
        });
    }

    private static ManagedWorld world(String name, WorldSettings settings) {
        WorldSpec spec = new WorldSpec(
                WorldEnvironment.NORMAL,
                WorldGenType.NORMAL,
                Optional.empty(),
                Optional.empty(),
                true,
                Optional.empty());
        return ManagedWorld.created(WorldName.of(name), spec, true, Optional.empty(), Instant.EPOCH)
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

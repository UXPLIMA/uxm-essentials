package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.PlayerDataPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerDataStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * End-to-end golden of the Phase-6 player-data and math placeholders through the real {@link Menus} open path. A
 * custom menu whose one item reads {@code %data_value_coins%} in its name, {@code {math: %data_number_coins% * 2}} and
 * {@code %meta_value_rank%} in its lore renders against a seeded data store (coins=50) and a seeded PDC (rank=VIP): the
 * name shows the raw stored string, the lore's math line shows the evaluated product, and the meta line shows the PDC
 * value. A missing key renders empty for {@code data_value_} and {@code 0} for {@code data_number_}, exercising both
 * the placeholder fallback and the math pass through the same renderer production uses.
 */
class DataPlaceholderGoldenTest {

    private static final String HOCON = """
            rows = 1
            items {
              panel {
                slots = ["0"],
                material = "PAPER",
                name = "%data_value_coins%",
                lore = ["{math: %data_number_coins% * 2}", "%meta_value_rank%"]
              }
            }
            """;

    private static final String MISSING_HOCON = """
            rows = 1
            items {
              panel {
                slots = ["0"],
                material = "PAPER",
                name = "%data_value_ghost%",
                lore = ["%data_number_ghost%"]
              }
            }
            """;

    private ServerMock server;
    private PlayerMock viewer;
    private Menus menus;
    private FakePlayerDataStore playerData;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Viewer");

        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        playerData = new FakePlayerDataStore();
        PlayerMeta playerMeta = new PlayerMeta(MockBukkit.createMockPlugin());
        playerData.set(viewer.getUniqueId(), "coins", "50");
        playerMeta.set(viewer, "rank", "VIP");
        PlayerDataPlaceholders.register(engine.bindings(), playerData, playerMeta);
        menus = engine.menus();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void dataAndMathAndMetaPlaceholdersRenderThroughTheOpenPath() {
        menus.registerSpec("panel", new MenuSpecLoader().parse(HOCON));
        menus.open(new PlayerRef(viewer.getUniqueId(), viewer.getName()), "panel", null);

        ItemStack item = topItem();
        assertThat(plainName(item)).isEqualTo("50");
        assertThat(plainLore(item)).containsExactly("100", "VIP");
    }

    @Test
    void aMissingKeyRendersEmptyForDataValueAndZeroForDataNumber() {
        menus.registerSpec("panel", new MenuSpecLoader().parse(MISSING_HOCON));
        menus.open(new PlayerRef(viewer.getUniqueId(), viewer.getName()), "panel", null);

        ItemStack item = topItem();
        assertThat(plainName(item)).isEmpty();
        assertThat(plainLore(item)).containsExactly("0");
    }

    private ItemStack topItem() {
        Inventory top = viewer.getOpenInventory().getTopInventory();
        return Objects.requireNonNull(top.getItem(0), "item at slot 0");
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    private static List<String> plainLore(ItemStack item) {
        // The body only: the title line the canon puts above it is asserted where the title is asserted.
        return TileText.body(item).stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                .toList();
    }

    private static final class FakePlayerDataStore implements PlayerDataStore {

        private final Map<UUID, Map<String, String>> data = new ConcurrentHashMap<>();

        @Override
        public Optional<String> get(UUID player, String key) {
            return Optional.ofNullable(data.getOrDefault(player, Map.of()).get(key));
        }

        @Override
        public double number(UUID player, String key, double fallback) {
            Optional<String> raw = get(player, key);
            if (raw.isEmpty()) {
                return fallback;
            }
            try {
                return Double.parseDouble(raw.get().trim());
            } catch (NumberFormatException notANumber) {
                return fallback;
            }
        }

        @Override
        public void set(UUID player, String key, String value) {
            data.computeIfAbsent(player, k -> new HashMap<>()).put(key, value);
        }

        @Override
        public double apply(UUID player, String key, NumericOp op, double operand) {
            throw new UnsupportedOperationException("reads only");
        }

        @Override
        public void remove(UUID player, String key) {
            Map<String, String> keys = data.get(player);
            if (keys != null) {
                keys.remove(key);
            }
        }

        @Override
        public Map<String, String> all(UUID player) {
            return Map.copyOf(data.getOrDefault(player, Map.of()));
        }
    }

    /** A synchronous scheduler that runs every hop inline so the open path completes within the test call. */
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}

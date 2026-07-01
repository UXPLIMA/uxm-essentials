package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.PlayerDataPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.port.PlayerDataStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the read side of the player-data vocabulary: {@code data_value_}/{@code data_number_} over an in-memory
 * {@link FakePlayerDataStore} mirroring the store contract, and {@code meta_value_} over a real {@link PlayerMeta} on a
 * {@link PlayerMock}'s persistent-data container. It asserts the raw-string, number-formatting and empty-on-missing
 * behaviour, that an offline viewer's meta reads empty, that underscored keys route, and that a resolver never throws.
 */
class PlayerDataPlaceholdersTest {

    private ServerMock server;
    private PlayerMock viewer;
    private MenuBindings bindings;
    private FakePlayerDataStore playerData;
    private PlayerMeta playerMeta;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Viewer");
        bindings = new MenuBindings();
        playerData = new FakePlayerDataStore();
        playerMeta = new PlayerMeta(MockBukkit.createMockPlugin());
        PlayerDataPlaceholders.register(bindings, playerData, playerMeta);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void dataValueReturnsTheRawStoredStringAndEmptyForAMissingKey() {
        playerData.set(viewer.getUniqueId(), "greeting", "hello there");

        assertThat(resolve("data_value_greeting")).contains("hello there");
        assertThat(resolve("data_value_absent")).contains("");
    }

    @Test
    void dataNumberFormatsAnIntegerWithoutTrailingZero() {
        playerData.set(viewer.getUniqueId(), "coins", "50");

        assertThat(resolve("data_number_coins")).contains("50");
    }

    @Test
    void dataNumberKeepsAFractionalValueAndDefaultsToZero() {
        playerData.set(viewer.getUniqueId(), "ratio", "12.5");
        playerData.set(viewer.getUniqueId(), "label", "not-a-number");

        assertThat(resolve("data_number_ratio")).contains("12.5");
        assertThat(resolve("data_number_label")).contains("0");
        assertThat(resolve("data_number_missing")).contains("0");
    }

    @Test
    void metaValueReturnsThePdcStringAndEmptyForAMissingKey() {
        playerMeta.set(viewer, "rank", "VIP");

        assertThat(resolve("meta_value_rank")).contains("VIP");
        assertThat(resolve("meta_value_absent")).contains("");
    }

    @Test
    void metaValueIsEmptyWhenTheViewerIsOffline() {
        MenuContext offline = MenuContext.of(new PlayerRef(UUID.randomUUID(), "Ghost"), null, 0);

        assertThat(bindings.placeholders().resolve("meta_value_rank", offline)).contains("");
    }

    @Test
    void underscoredKeysRouteToTheCorrectStore() {
        playerData.set(viewer.getUniqueId(), "coins_earned", "7");

        assertThat(resolve("data_value_coins_earned")).contains("7");
    }

    @Test
    void everyFamilyIsClaimedForValidationAndNeverThrows() {
        assertThat(bindings.placeholders().has("data_value_x")).isTrue();
        assertThat(bindings.placeholders().has("data_number_x")).isTrue();
        assertThat(bindings.placeholders().has("meta_value_x")).isTrue();

        assertThatCode(() -> {
                    resolve("data_value_x");
                    resolve("data_number_x");
                    resolve("meta_value_x");
                })
                .doesNotThrowAnyException();
    }

    private Optional<String> resolve(String id) {
        MenuContext ctx = MenuContext.of(new PlayerRef(viewer.getUniqueId(), viewer.getName()), null, 0);
        return bindings.placeholders().resolve(id, ctx);
    }

    /** An in-memory {@link PlayerDataStore} whose reads mirror the real store; only {@code get}/{@code number} matter here. */
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
}

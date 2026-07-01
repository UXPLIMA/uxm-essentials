package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.DataActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerDataStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Coverage of the player-data action pack. The {@code data-*} actions run over an in-memory
 * {@link FakePlayerDataStore} whose arithmetic mirrors the real {@link PlayerDataStore} contract, so the SET / ADD /
 * SUB / MUL / DIV chain (including the divide-by-zero no-change) is asserted deterministically without a database.
 * The {@code meta-*} actions run over a real {@link PlayerMeta} on a {@link PlayerMock}'s persistent-data container,
 * so their PDC round-trip is asserted concretely. The {@code <key> <value…>} grammar and the number parse are pinned
 * by plain-JUnit assertions.
 */
class DataActionsTest {

    private ServerMock server;
    private PlayerMock viewer;
    private MenuBindings bindings;
    private RecordingLogger log;
    private FakePlayerDataStore playerData;
    private PlayerMeta playerMeta;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Viewer");
        bindings = new MenuBindings();
        log = new RecordingLogger();
        playerData = new FakePlayerDataStore();
        playerMeta = new PlayerMeta(MockBukkit.createMockPlugin());
        DataActions.register(bindings, playerData, playerMeta, log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- durable player data -------------------------------------------------------------------------------------

    @Test
    void dataAddAccumulatesOntoAMissingKey() {
        invoke("data-add", "coins 5");
        invoke("data-add", "coins 3");

        assertThat(playerData.number(viewer.getUniqueId(), "coins", -1)).isEqualTo(8.0);
    }

    @Test
    void dataMulThenSubThenDivWalkTheStoredValue() {
        invoke("data-add", "coins 5");
        invoke("data-add", "coins 3"); // 8
        invoke("data-mul", "coins 2"); // 16
        assertThat(playerData.number(viewer.getUniqueId(), "coins", -1)).isEqualTo(16.0);

        invoke("data-sub", "coins 6"); // 10
        assertThat(playerData.number(viewer.getUniqueId(), "coins", -1)).isEqualTo(10.0);

        invoke("data-div", "coins 0"); // divide-by-zero is a documented no-change
        assertThat(playerData.number(viewer.getUniqueId(), "coins", -1)).isEqualTo(10.0);
    }

    @Test
    void dataSetKeepsTheSpacesInTheValue() {
        invoke("data-set", "name Steve Jobs");

        assertThat(playerData.get(viewer.getUniqueId(), "name")).contains("Steve Jobs");
    }

    @Test
    void setDataAliasIsRegistered() {
        invoke("set-data", "greeting hello there");

        assertThat(playerData.get(viewer.getUniqueId(), "greeting")).contains("hello there");
    }

    @Test
    void dataRemoveDropsTheKey() {
        invoke("data-add", "coins 5");
        invoke("data-remove", "coins");

        assertThat(playerData.get(viewer.getUniqueId(), "coins")).isEmpty();
    }

    @Test
    void aNonNumericOperandIsAFailSoftNoOpThatWarns() {
        invoke("data-add", "coins 5");

        assertThatCode(() -> invoke("data-add", "coins lots")).doesNotThrowAnyException();

        assertThat(playerData.number(viewer.getUniqueId(), "coins", -1)).isEqualTo(5.0);
        assertThat(log.warnings).anyMatch(line -> line.contains("malformed_number"));
    }

    // --- transient PDC meta --------------------------------------------------------------------------------------

    @Test
    void metaSetStoresOnThePlayersPdc() {
        invoke("meta-set", "rank vip");

        assertThat(playerMeta.get(viewer, "rank")).contains("vip");
    }

    @Test
    void metaAddAccumulatesOnThePlayersPdc() {
        invoke("meta-add", "points 5");
        invoke("meta-add", "points 3");

        assertThat(playerMeta.get(viewer, "points")).contains("8");
    }

    @Test
    void metaRemoveDropsThePdcKey() {
        invoke("meta-add", "points 5");
        invoke("meta-remove", "points");

        assertThat(playerMeta.has(viewer, "points")).isFalse();
    }

    // --- pure grammar --------------------------------------------------------------------------------------------

    @Test
    void keyValueSplitsKeyFromTheRemainder() {
        DataActions.KeyValue arg = DataActions.KeyValue.parse("name Steve Jobs");

        assertThat(arg.key()).isEqualTo("name");
        assertThat(arg.value()).isEqualTo("Steve Jobs");
    }

    @Test
    void keyValueKeepsInternalSpacesInTheValue() {
        DataActions.KeyValue arg = DataActions.KeyValue.parse("greeting  hello   there");

        assertThat(arg.key()).isEqualTo("greeting");
        assertThat(arg.value()).isEqualTo("hello   there");
    }

    @Test
    void keyValueOfABlankArgumentHasAnEmptyKey() {
        DataActions.KeyValue arg = DataActions.KeyValue.parse("   ");

        assertThat(arg.key()).isEmpty();
        assertThat(arg.value()).isEmpty();
    }

    @Test
    void keyValueWithNoValueLeavesTheValueEmpty() {
        DataActions.KeyValue arg = DataActions.KeyValue.parse("loner");

        assertThat(arg.key()).isEqualTo("loner");
        assertThat(arg.value()).isEmpty();
    }

    @Test
    void parseNumberRejectsANonNumericToken() {
        assertThat(DataActions.parseNumber("lots")).isEmpty();
        assertThat(DataActions.parseNumber("  5  ")).hasValue(5.0);
    }

    // --- harness -------------------------------------------------------------------------------------------------

    /** Builds the context the click listener would build, then fires the handler {@code id}. */
    private void invoke(String id, String arg) {
        Consumer<MenuActionContext> handler =
                bindings.action(id).orElseThrow(() -> new AssertionError("action not registered: " + id));
        PlayerRef ref = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        MenuActionContext ctx =
                new MenuActionContext(MenuContext.of(ref, null, 0), viewer, ClickKind.LEFT, Map.of("value", arg));
        handler.accept(ctx);
    }

    /** An in-memory {@link PlayerDataStore} whose {@link #apply} mirrors the documented NumericOp contract. */
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
            double current = number(player, key, 0.0);
            if (op == NumericOp.DIV && operand == 0) {
                return current; // documented no-change
            }
            double next =
                    switch (op) {
                        case SET -> operand;
                        case ADD -> current + operand;
                        case SUB -> current - operand;
                        case MUL -> current * operand;
                        case DIV -> current / operand;
                    };
            set(player, key, format(next));
            return next;
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

        private static String format(double value) {
            if (Double.isFinite(value) && value == Math.floor(value) && Math.abs(value) < 1e15) {
                return Long.toString((long) value);
            }
            return Double.toString(value);
        }
    }

    /** Captures expanded warn lines so a test can assert what the console operator logger recorded. */
    private static final class RecordingLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(expand(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}

        private static String expand(String message, Object... args) {
            String expanded = Objects.requireNonNull(message);
            for (Object arg : args) {
                expanded = expanded.replaceFirst("\\{}", String.valueOf(arg));
            }
            return expanded;
        }
    }
}

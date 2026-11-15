package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.ItemReturnStore;
import com.uxplima.uxmessentials.economy.application.port.SellerInventory;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

class ReturnUnsoldItemsTest {

    private final Set<PlayerRef> online = new HashSet<>(Set.of(ALEX));
    private final Map<String, Integer> inventory = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Integer>> kept = new HashMap<>();
    private final List<String> lines = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    private final ReturnUnsoldItems returns = new ReturnUnsoldItems(
            new InlineScheduler(),
            new FakeInventory(),
            new FakeStore(),
            new EconomyNotifier(
                    (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) -> {
                        lines.add(key.key() + placeholders.getOrDefault("count", ""));
                        return key.key();
                    },
                    (viewer, text) -> {}),
            new FakeLogger());

    @Test
    void aRefusedSaleGivesEverythingBack() {
        returns.sell(ALEX, Map.of("diamond", 5), taken -> Map.of());

        assertThat(inventory).isEqualTo(Map.of("diamond", 5));
        assertThat(kept).isEmpty();
    }

    @Test
    void onlyWhatTheSaleDidNotPayForGoesBack() {
        Map<String, Integer> taken = new LinkedHashMap<>();
        taken.put("diamond", 5);
        taken.put("coal", 64);
        taken.put("iron_ingot", 10);

        returns.sell(ALEX, taken, items -> Map.of("diamond", 5, "iron_ingot", 4));

        assertThat(inventory).isEqualTo(Map.of("coal", 64, "iron_ingot", 6));
    }

    @Test
    void aSaleThatThrowsGivesEverythingBackAndSaysSo() {
        returns.sell(ALEX, Map.of("diamond", 5), taken -> {
            throw new IllegalStateException("database down");
        });

        assertThat(inventory).isEqualTo(Map.of("diamond", 5));
        assertThat(lines).containsExactly("wallet.sell-failed");
        assertThat(errors).hasSize(1);
    }

    @Test
    void aSellerWhoLeftDuringTheSaleGetsTheItemsOnTheNextJoin() {
        returns.sell(ALEX, Map.of("diamond", 5), taken -> {
            online.remove(ALEX);
            return Map.of();
        });

        assertThat(inventory).isEmpty();
        assertThat(kept).isEqualTo(Map.of(ALEX.uuid(), Map.of("diamond", 5)));

        online.add(ALEX);
        returns.deliverKept(ALEX);

        assertThat(inventory).isEqualTo(Map.of("diamond", 5));
        assertThat(kept).isEmpty();
        assertThat(lines).containsExactly("wallet.sell-returned5");
    }

    @Test
    void itemsKeptForASellerWhoLeavesAgainBeforeTheHandOverStayKept() {
        kept.put(ALEX.uuid(), new HashMap<>(Map.of("coal", 3)));
        online.remove(ALEX);

        returns.deliverKept(ALEX);

        assertThat(kept).isEqualTo(Map.of(ALEX.uuid(), Map.of("coal", 3)));
        assertThat(lines).isEmpty();
    }

    /** Runs every task at once; an entity task for a player who is not online takes its retired branch. */
    private final class InlineScheduler implements Scheduler {

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
            if (online.contains(player)) {
                task.run();
            }
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            if (online.contains(player)) {
                task.run();
            } else {
                retired.run();
            }
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

    private final class FakeInventory implements SellerInventory {

        @Override
        public boolean giveBack(PlayerRef seller, Map<String, Integer> items) {
            if (!online.contains(seller)) {
                return false;
            }
            items.forEach((id, count) -> inventory.merge(id, count, Integer::sum));
            return true;
        }
    }

    private final class FakeStore implements ItemReturnStore {

        @Override
        public void keep(UUID owner, Map<String, Integer> items) {
            Map<String, Integer> mine = kept.computeIfAbsent(owner, any -> new HashMap<>());
            items.forEach((id, count) -> mine.merge(id, count, Integer::sum));
        }

        @Override
        public Map<String, Integer> takeAll(UUID owner) {
            Map<String, Integer> mine = kept.remove(owner);
            return mine == null ? Map.of() : mine;
        }
    }

    private final class FakeLogger implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}

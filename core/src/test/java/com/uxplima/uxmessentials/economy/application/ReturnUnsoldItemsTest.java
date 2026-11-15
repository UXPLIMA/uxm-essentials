package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.ItemReturnStore;
import com.uxplima.uxmessentials.economy.application.port.SellerInventory;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.junit.jupiter.api.Test;

class ReturnUnsoldItemsTest {

    private static final Currency DOLLARS =
            Currency.builder(CurrencyId.of("dollars")).build();

    private final Set<PlayerRef> online = new HashSet<>(Set.of(ALEX));
    private final Map<String, Integer> inventory = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Integer>> kept = new HashMap<>();
    private final List<String> lines = new ArrayList<>();
    private final RecordingLogger log = new RecordingLogger();
    private final InlineScheduler scheduler = new InlineScheduler();
    private boolean noticesFail;

    private final EconomyNotifier notifier = new EconomyNotifier(
            (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) -> {
                if (noticesFail) {
                    throw new IllegalStateException("chat is down");
                }
                lines.add(key.key() + placeholders.getOrDefault("count", ""));
                return key.key();
            },
            (viewer, text) -> {});

    private final ReturnUnsoldItems returns =
            new ReturnUnsoldItems(scheduler, new FakeInventory(), new FakeStore(), notifier, log);

    @Test
    void aRefusedSaleGivesEverythingBack() {
        returns.sell(ALEX, Map.of("diamond", 5), (taken, paid) -> Map.of());

        assertThat(inventory).isEqualTo(Map.of("diamond", 5));
        assertThat(kept).isEmpty();
    }

    @Test
    void onlyWhatTheSaleDidNotPayForGoesBack() {
        Map<String, Integer> taken = new LinkedHashMap<>();
        taken.put("diamond", 5);
        taken.put("coal", 64);
        taken.put("iron_ingot", 10);

        returns.sell(ALEX, taken, (items, paid) -> Map.of("diamond", 5, "iron_ingot", 4));

        assertThat(inventory).isEqualTo(Map.of("coal", 64, "iron_ingot", 6));
    }

    @Test
    void aSaleThatThrowsGivesBackOnlyWhatItHadNotBeenPaidFor() {
        returns.sell(ALEX, Map.of("diamond", 5, "coal", 64), (taken, paid) -> {
            paid.paid(Map.of("diamond", 5));
            throw new IllegalStateException("database down");
        });

        assertThat(inventory).isEqualTo(Map.of("coal", 64));
        assertThat(lines).containsExactly("wallet.sell-failed");
        assertThat(log.errors()).hasSize(1);
    }

    @Test
    void aFailingNoticeDoesNotCostTheItems() {
        noticesFail = true;

        returns.sell(ALEX, Map.of("diamond", 5), (taken, paid) -> {
            throw new IllegalStateException("database down");
        });

        assertThat(inventory).isEqualTo(Map.of("diamond", 5));
    }

    @Test
    void aCreditThatAppliedIsNotUndoneByAFailureAfterIt() {
        InMemoryBank bank = new InMemoryBank(DOLLARS);
        PublishingEconomyProvider economy = new PublishingEconomyProvider(
                bank,
                event -> {
                    throw new IllegalStateException("event bus down");
                },
                CLOCK,
                log);
        SellItem sellItem = new SellItem(
                economy,
                material -> Optional.of(Worth.of(BigDecimal.TEN, "dollars")),
                notifier,
                DOLLARS,
                List.of(DOLLARS),
                log);

        returns.sell(
                ALEX,
                Map.of("diamond", 5),
                (items, paid) -> sellItem.sell(ALEX, "diamond", 5, () -> paid.paid(items))
                                .sold()
                        ? items
                        : Map.of());

        assertThat(bank.balance(ALEX, DOLLARS).amount()).isEqualByComparingTo("50");
        assertThat(inventory).isEmpty();
        assertThat(kept).isEmpty();
    }

    @Test
    void aSellerWhoLeftDuringTheSaleGetsTheItemsOnTheNextJoin() {
        returns.sell(ALEX, Map.of("diamond", 5), (taken, paid) -> {
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

    @Test
    void aStopKeepsASaleTheSchedulerNeverRan() {
        scheduler.paused = true;
        returns.sell(ALEX, Map.of("diamond", 5), (taken, paid) -> Map.of("diamond", 5));

        returns.stop();
        scheduler.runQueued();

        assertThat(kept).isEqualTo(Map.of(ALEX.uuid(), Map.of("diamond", 5)));
        assertThat(inventory).isEmpty();
    }

    @Test
    void aStopBeforeTheHandOverKeepsTheItemsAgain() {
        kept.put(ALEX.uuid(), new HashMap<>(Map.of("coal", 3)));
        scheduler.paused = true;

        returns.deliverKept(ALEX);
        returns.stop();
        scheduler.runQueued();

        assertThat(kept).isEqualTo(Map.of(ALEX.uuid(), Map.of("coal", 3)));
        assertThat(inventory).isEmpty();
    }

    /**
     * Runs every task at once, or queues it while paused, the way a disabling plugin's scheduler holds work it will
     * never run. An entity task for a player who is not online takes its retired branch.
     */
    private final class InlineScheduler implements Scheduler {

        private final Deque<Runnable> queued = new ArrayDeque<>();
        private boolean paused;

        private void submit(Runnable task) {
            if (paused) {
                queued.add(task);
            } else {
                task.run();
            }
        }

        void runQueued() {
            paused = false;
            while (!queued.isEmpty()) {
                queued.poll().run();
            }
        }

        @Override
        public void onGlobal(Runnable task) {
            submit(task);
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            submit(task);
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            submit(() -> {
                if (online.contains(player)) {
                    task.run();
                }
            });
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            submit(() -> {
                if (online.contains(player)) {
                    task.run();
                } else {
                    retired.run();
                }
            });
        }

        @Override
        public void async(Runnable task) {
            submit(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            submit(task);
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
}

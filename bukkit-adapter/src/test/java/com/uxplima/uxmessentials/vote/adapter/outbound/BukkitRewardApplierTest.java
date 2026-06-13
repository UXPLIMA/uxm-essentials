package com.uxplima.uxmessentials.vote.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.reward.ItemReward;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@link BukkitRewardApplier}: an online grant dispatches its commands (proven by
 * the global scheduler hop the dispatcher takes), gives its items to the voter, drops the overflow when the
 * inventory is full, and skips an unknown material without throwing; an offline grant enqueues only its
 * commands through the {@link VoteRepository} and grants no items.
 */
class BukkitRewardApplierTest {

    private ServerMock server;
    private PlayerMock voter;
    private RecordingScheduler scheduler;
    private RecordingRepository repository;
    private BukkitRewardApplier applier;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        voter = server.addPlayer("Alice");
        scheduler = new RecordingScheduler();
        repository = new RecordingRepository();
        applier = new BukkitRewardApplier(repository, new BukkitRewardDispatcher(scheduler), scheduler, new NoLog());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onlineGrantDispatchesItsCommandsOnTheGlobalThread() {
        applier.apply(ref(), true, new RewardGrant(List.of("eco give {player} 100"), List.of(), List.of(), List.of()));

        // The dispatcher hops onto the global region thread to run the console command.
        assertThat(scheduler.dispatchedOn).contains("global");
        // No items were requested, so the inventory stays empty and nothing is queued for an online voter.
        assertThat(repository.enqueued).isEmpty();
    }

    @Test
    void onlineGrantGivesItsItemsToTheVoter() {
        ItemReward diamond = new ItemReward("DIAMOND", 3, Optional.empty(), List.of());
        applier.apply(ref(), true, new RewardGrant(List.of(), List.of(), List.of(), List.of(diamond)));

        assertThat(voter.getInventory().contains(Material.DIAMOND, 3)).isTrue();
        assertThat(scheduler.dispatchedOn).contains("entity");
    }

    @Test
    void overflowIsDroppedWhenTheInventoryIsFull() {
        fillInventory(voter);
        ItemReward diamond = new ItemReward("DIAMOND", 5, Optional.empty(), List.of());

        applier.apply(ref(), true, new RewardGrant(List.of(), List.of(), List.of(), List.of(diamond)));

        // The full inventory cannot take the diamonds, so they drop as an item entity at the voter's feet.
        long dropped = voter.getWorld().getEntities().stream()
                .filter(e -> e instanceof org.bukkit.entity.Item)
                .count();
        assertThat(dropped).isPositive();
    }

    @Test
    void anUnknownMaterialIsSkippedWithoutThrowing() {
        ItemReward bogus = new ItemReward("NOT_A_REAL_MATERIAL", 1, Optional.empty(), List.of());

        assertThatCode(() ->
                        applier.apply(ref(), true, new RewardGrant(List.of(), List.of(), List.of(), List.of(bogus))))
                .doesNotThrowAnyException();
        assertThat(voter.getInventory().isEmpty()).isTrue();
    }

    @Test
    void offlineGrantEnqueuesOnlyItsCommands() {
        PlayerRef offline = new PlayerRef(java.util.UUID.randomUUID(), "Ghost");
        ItemReward diamond = new ItemReward("DIAMOND", 1, Optional.empty(), List.of());
        RewardGrant grant = new RewardGrant(
                List.of("eco give {player} 50"), List.of("<green>hi"), List.of("<gold>bye"), List.of(diamond));

        applier.apply(offline, false, grant);

        assertThat(repository.enqueued).hasSize(1);
        QueuedReward queued = repository.enqueued.get(0);
        assertThat(queued.player()).isEqualTo(offline);
        // The {player} token stays raw for the join-time drain; only commands are durable for an offline voter.
        assertThat(queued.commands()).containsExactly("eco give {player} 50");
    }

    @Test
    void offlineGrantWithNoCommandsEnqueuesNothing() {
        PlayerRef offline = new PlayerRef(java.util.UUID.randomUUID(), "Ghost");

        applier.apply(offline, false, new RewardGrant(List.of(), List.of("<green>hi"), List.of(), List.of()));

        assertThat(repository.enqueued).isEmpty();
    }

    private PlayerRef ref() {
        return new PlayerRef(voter.getUniqueId(), voter.getName());
    }

    private static void fillInventory(PlayerMock player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        }
    }

    /** A scheduler that runs every task inline and records the thread method used. */
    private static final class RecordingScheduler implements Scheduler {
        private final List<String> dispatchedOn = new ArrayList<>();

        @Override
        public void onGlobal(Runnable task) {
            dispatchedOn.add("global");
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            dispatchedOn.add("region");
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            dispatchedOn.add("entity");
            task.run();
        }

        @Override
        public void async(Runnable task) {
            dispatchedOn.add("async");
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Records every enqueued offline reward batch; the rest of the contract is unused here. */
    private static final class RecordingRepository implements VoteRepository {
        private final List<QueuedReward> enqueued = new ArrayList<>();

        @Override
        public int partyCount() {
            return 0;
        }

        @Override
        public void setPartyCount(int count) {}

        @Override
        public int incrementAndGetPartyCount() {
            return 1;
        }

        @Override
        public void enqueue(QueuedReward reward) {
            enqueued.add(reward);
        }

        @Override
        public List<QueuedReward> drainFor(PlayerRef player) {
            return List.of();
        }

        @Override
        public boolean hasPending(PlayerRef player) {
            return false;
        }

        @Override
        public VoteTally totalsOf(PlayerRef player) {
            return VoteTally.empty();
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {}

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            return List.of();
        }

        @Override
        public void markPartyParticipant(PlayerRef player) {}

        @Override
        public java.util.Set<java.util.UUID> partyParticipants() {
            return java.util.Set.of();
        }

        @Override
        public void clearPartyParticipants() {}

        @Override
        public long partyPeriodKey() {
            return 0L;
        }

        @Override
        public void setPartyPeriodKey(long key) {}

        @Override
        public int thresholdOverride() {
            return 0;
        }

        @Override
        public void setThresholdOverride(int override) {}

        @Override
        public boolean claimPartyFire(int threshold) {
            return false;
        }

        @Override
        public void resetTotals(PlayerRef player) {}

        @Override
        public java.util.Optional<java.time.Instant> lastVoteAtSite(PlayerRef player, String site) {
            return java.util.Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, java.time.Instant at) {}
    }

    private static final class NoLog implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

package com.uxplima.uxmessentials.economy.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.adapter.outbound.EconomyMaintenanceTask;
import com.uxplima.uxmessentials.economy.application.port.EconomyMaintenance;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * The purge filter is the safety-critical part of {@link EconomyMaintenanceTask}: only owners whose last login is
 * known and before the cutoff are eligible, a protected owner is never a candidate, and a dry-run pass reports
 * counts without ever calling the destructive purge. Driven with fakes so no database or server is needed.
 */
class EconomyMaintenanceTaskTest {

    private static final long NOW = Duration.ofDays(1000).toMillis();
    private static final long DAY = Duration.ofDays(1).toMillis();

    @Test
    void dryRunReportsTheInactiveCandidatesWithoutPurging() {
        PlayerRef active = ref("Active");
        PlayerRef inactive = ref("Inactive");
        PlayerRef protectedOwner = ref("Protected");
        PlayerRef unknown = ref("Unknown");
        FakeMaintenance maintenance = new FakeMaintenance();
        maintenance.owners = List.of(active, inactive, protectedOwner, unknown);
        maintenance.protectedOwners = Set.of(protectedOwner.uuid());
        maintenance.transactionsBefore = 7;
        Map<UUID, Long> seen = new HashMap<>();
        seen.put(active.uuid(), NOW); // online / just seen
        seen.put(inactive.uuid(), NOW - 200 * DAY);
        seen.put(protectedOwner.uuid(), NOW - 200 * DAY);
        seen.put(unknown.uuid(), 0L); // never recorded

        EconomyMaintenanceTask task = task(maintenance, seen::get, true);
        EconomyMaintenanceTask.Report report = task.runOnce(true);

        assertThat(report.prunedTransactions()).isEqualTo(7); // counted, not deleted
        assertThat(report.purgedOwners()).isEqualTo(1); // only the inactive, unprotected, known owner
        assertThat(maintenance.purged).isNull(); // dry run never calls the destructive purge
    }

    @Test
    void appliedPassPurgesOnlyTheInactiveUnprotectedOwner() {
        PlayerRef inactive = ref("Inactive");
        PlayerRef protectedOwner = ref("Protected");
        FakeMaintenance maintenance = new FakeMaintenance();
        maintenance.owners = List.of(inactive, protectedOwner);
        maintenance.protectedOwners = Set.of(protectedOwner.uuid());
        maintenance.transactionsBefore = 3;
        Map<UUID, Long> seen = new HashMap<>();
        seen.put(inactive.uuid(), NOW - 200 * DAY);
        seen.put(protectedOwner.uuid(), NOW - 200 * DAY);

        EconomyMaintenanceTask task = task(maintenance, seen::get, false);
        EconomyMaintenanceTask.Report report = task.runOnce(false);

        assertThat(report.prunedTransactions()).isEqualTo(3); // deleted
        assertThat(maintenance.purged).containsExactly(inactive.uuid());
        assertThat(report.purgedOwners()).isEqualTo(1);
    }

    private static EconomyMaintenanceTask task(
            EconomyMaintenance maintenance, EconomyMaintenanceTask.PlayerLastSeen lastSeen, boolean dryRun) {
        return new EconomyMaintenanceTask(
                maintenance,
                new NoopScheduler(),
                new NoopLogger(),
                Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC),
                lastSeen,
                true,
                Duration.ofHours(24),
                90,
                90,
                dryRun);
    }

    private static PlayerRef ref(String name) {
        return new PlayerRef(UUID.randomUUID(), name);
    }

    private static final class FakeMaintenance implements EconomyMaintenance {
        private int transactionsBefore;
        private List<PlayerRef> owners = List.of();
        private Set<UUID> protectedOwners = Set.of();
        private Collection<UUID> purged;

        @Override
        public int countTransactionsBefore(long cutoffMillis) {
            return transactionsBefore;
        }

        @Override
        public int deleteTransactionsBefore(long cutoffMillis) {
            return transactionsBefore;
        }

        @Override
        public List<PlayerRef> allOwners() {
            return owners;
        }

        @Override
        public Set<UUID> protectedOwners() {
            return protectedOwners;
        }

        @Override
        public int purgeOwners(Collection<UUID> owners) {
            this.purged = owners;
            return owners.size();
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private static final class NoopScheduler implements Scheduler {
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

package com.uxplima.uxmessentials.vaults.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@code PurgeInactiveVaults} computes the inactivity cutoff ({@code now - inactiveFor}) and delegates to
 * {@link VaultRepository#deleteUntouchedBefore(Instant)}, returning the row count the repository reports. A
 * zero duration leaves the cutoff at {@code now}; a negative duration is rejected; {@code now} must be present.
 * A fake repository captures the cutoff and reports a fixed count.
 */
class PurgeInactiveVaultsTest {

    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");

    @Test
    void purgeComputesTheCutoffDelegatesAndReturnsTheCount() {
        CapturingRepository repository = new CapturingRepository(4);
        PurgeInactiveVaults purge = new PurgeInactiveVaults(repository);

        int removed = purge.purge(Duration.ofDays(30), NOW);

        assertThat(removed).isEqualTo(4);
        assertThat(repository.cutoff).isEqualTo(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    void aZeroDurationMakesTheCutoffNow() {
        CapturingRepository repository = new CapturingRepository(0);
        PurgeInactiveVaults purge = new PurgeInactiveVaults(repository);

        purge.purge(Duration.ZERO, NOW);

        assertThat(repository.cutoff).isEqualTo(NOW);
    }

    @Test
    void aNegativeDurationIsRejected() {
        CapturingRepository repository = new CapturingRepository(0);
        PurgeInactiveVaults purge = new PurgeInactiveVaults(repository);

        assertThatThrownBy(() -> purge.purge(Duration.ofDays(-1), NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.cutoff).isNull(); // nothing was deleted
    }

    /** Records the cutoff passed to {@link #deleteUntouchedBefore} and reports a fixed removed count. */
    private static final class CapturingRepository implements VaultRepository {
        private final int removed;
        private @Nullable Instant cutoff;

        CapturingRepository(int removed) {
            this.removed = removed;
        }

        @Override
        public int deleteUntouchedBefore(Instant cutoff) {
            this.cutoff = cutoff;
            return removed;
        }

        @Override
        public Optional<Vault> find(VaultId id) {
            throw new UnsupportedOperationException("not exercised");
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            throw new UnsupportedOperationException("not exercised");
        }

        @Override
        public List<VaultSummary> summaries(PlayerRef owner) {
            throw new UnsupportedOperationException("not exercised");
        }

        @Override
        public int count(PlayerRef owner) {
            throw new UnsupportedOperationException("not exercised");
        }

        @Override
        public void save(Vault vault) {
            throw new UnsupportedOperationException("not exercised");
        }

        @Override
        public void delete(VaultId id) {
            throw new UnsupportedOperationException("not exercised");
        }
    }
}

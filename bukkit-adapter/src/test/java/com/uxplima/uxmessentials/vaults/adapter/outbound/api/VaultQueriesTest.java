package com.uxplima.uxmessentials.vaults.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmVault;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultSizeQuota;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published vault query: it reads summaries rather than whole vaults, it numbers them the way the owner
 * does, and the limit and the size are the ones the commands would enforce.
 */
class VaultQueriesTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");

    private FakeVaultRepository repository;
    private FixedQuota permissions;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new FakeVaultRepository();
        permissions = new FixedQuota();
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @Test
    void everyReadRunsOffTheCallingThread() {
        queries().list(OWNER.uuid()).join();
        queries().get(OWNER.uuid(), 1).join();
        queries().count(OWNER.uuid()).join();
        queries().limit(OWNER.uuid()).join();
        queries().rows(OWNER.uuid()).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(5);
    }

    @Test
    void theViewCarriesTheNameAndTheIconTheOwnerChose() {
        repository.put(new VaultSummary(2, "Ores", "DIAMOND_ORE"));

        UxmVault vault = queries().list(OWNER.uuid()).join().getFirst();

        assertThat(vault.ownerId()).isEqualTo(OWNER.uuid());
        assertThat(vault.index()).isEqualTo(2);
        assertThat(vault.displayName()).contains("Ores");
        assertThat(vault.icon()).contains("DIAMOND_ORE");
        assertThat(vault.label()).isEqualTo("Ores");
    }

    @Test
    void anUnnamedVaultFallsBackToItsNumber() {
        repository.put(new VaultSummary(3, null, null));

        UxmVault vault = queries().get(OWNER.uuid(), 3).join().orElseThrow();

        assertThat(vault.displayName()).isEmpty();
        assertThat(vault.icon()).isEmpty();
        assertThat(vault.label()).isEqualTo("3");
    }

    @Test
    void aVaultTheOwnerNeverOpenedIsAbsent() {
        repository.put(new VaultSummary(1, null, null));

        assertThat(queries().get(OWNER.uuid(), 1).join()).isPresent();
        assertThat(queries().get(OWNER.uuid(), 5).join()).isEmpty();
    }

    @Test
    void aVaultNumberBelowOneIsRefusedBeforeAnythingIsScheduled() {
        VaultQueries queries = queries();

        assertThatThrownBy(() -> queries.get(OWNER.uuid(), 0).join()).isInstanceOf(IllegalArgumentException.class);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    @Test
    void countIsTheNumberTheOwnerHasOpened() {
        repository.put(new VaultSummary(1, null, null));
        repository.put(new VaultSummary(2, null, null));

        assertThat(queries().count(OWNER.uuid()).join()).isEqualTo(2);
    }

    @Test
    void theLimitIsTheOneTheServerWouldEnforceAndUnlimitedHasNoNumber() {
        permissions.quota = Permissions.QuotaResult.limited(6);
        assertThat(queries().limit(OWNER.uuid()).join()).contains(6);

        permissions.quota = Permissions.QuotaResult.unlimited();
        assertThat(queries().limit(OWNER.uuid()).join()).isEmpty();
    }

    @Test
    void theRowCountIsTheResolvedSizeOfTheNextVault() {
        permissions.quota = Permissions.QuotaResult.limited(4);

        assertThat(queries().rows(OWNER.uuid()).join()).isEqualTo(4);
    }

    @Test
    void aVaultsContentsAreNeverPublished() {
        repository.put(new VaultSummary(1, "Ores", null));

        assertThat(queries().list(OWNER.uuid()).join().getFirst().toString())
                .as("the view is built from a summary, so there is nothing here to leak an item stack through")
                .doesNotContain("contents");
        assertThat(repository.loadedWholeVaults)
                .as("reading a list must not deserialise a single item stack")
                .isFalse();
    }

    private VaultQueries queries() {
        return new VaultQueries(
                repository,
                new VaultAmountQuota(permissions, 3),
                new VaultSizeQuota(permissions, 3),
                new QueryDoubles.MapLookup().with(OWNER),
                scheduler);
    }

    private static final class FakeVaultRepository implements VaultRepository {

        private final List<VaultSummary> summaries = new ArrayList<>();
        private boolean loadedWholeVaults;

        void put(VaultSummary summary) {
            summaries.add(summary);
        }

        @Override
        public Optional<Vault> find(VaultId id) {
            loadedWholeVaults = true;
            return Optional.empty();
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            return summaries.stream().map(VaultSummary::index).toList();
        }

        @Override
        public List<VaultSummary> summaries(PlayerRef owner) {
            return List.copyOf(summaries);
        }

        @Override
        public int count(PlayerRef owner) {
            return summaries.size();
        }

        @Override
        public void save(Vault vault) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void delete(VaultId id) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public int deleteUntouchedBefore(Instant cutoff) {
            throw new AssertionError("a query must never write");
        }
    }

    private static final class FixedQuota implements Permissions {

        private QuotaResult quota = QuotaResult.limited(3);

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return quota;
        }
    }
}

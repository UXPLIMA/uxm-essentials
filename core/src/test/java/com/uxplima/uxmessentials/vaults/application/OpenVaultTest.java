package com.uxplima.uxmessentials.vaults.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@code OpenVault} resolves the two numbered-quota families through the shared {@code Permissions} reducer and
 * applies them: a request within the resolved amount quota allocates and persists a fresh vault at the resolved
 * size; a request beyond it is refused with {@link VaultError#AMOUNT_EXCEEDED} and writes nothing; an
 * already-owned vault re-opens regardless of the amount quota (a player never loses access to stored items).
 * The repository and permissions are in-memory fakes so the quota rule is asserted without a database.
 */
class OpenVaultTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-31T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void allocatesAFreshVaultWithinTheAmountQuotaAtTheResolvedSize() {
        FakeRepository repository = new FakeRepository();
        OpenVault openVault = openVaultWith(repository, 3, 6);

        Result<Vault, VaultError> result = openVault.open(OWNER, 2);

        assertThat(result.isOk()).isTrue();
        Vault vault = result.orElseThrow();
        assertThat(vault.index()).isEqualTo(2);
        assertThat(vault.size().rows()).isEqualTo(6);
        assertThat(repository.find(VaultId.of(OWNER, 2))).isPresent(); // persisted up front
    }

    @Test
    void refusesAnIndexBeyondTheAmountQuotaAndWritesNothing() {
        FakeRepository repository = new FakeRepository();
        OpenVault openVault = openVaultWith(repository, 1, 6);

        Result<Vault, VaultError> result = openVault.open(OWNER, 4);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(VaultError.AMOUNT_EXCEEDED);
        assertThat(repository.find(VaultId.of(OWNER, 4))).isEmpty();
    }

    @Test
    void reopensAnExistingVaultEvenWhenItIsBeyondACurrentAmountQuota() {
        FakeRepository repository = new FakeRepository();
        repository.save(Vault.allocate(
                VaultId.of(OWNER, 5), new com.uxplima.uxmessentials.vaults.domain.VaultSize(3), CLOCK.instant()));
        OpenVault openVault = openVaultWith(repository, 1, 6); // amount shrank to 1, vault 5 already exists

        Result<Vault, VaultError> result = openVault.open(OWNER, 5);

        assertThat(result.isOk()).isTrue();
        assertThat(result.orElseThrow().size().rows()).isEqualTo(6); // re-open adopts the current size quota
    }

    private static OpenVault openVaultWith(VaultRepository repository, int amount, int size) {
        FixedQuotas permissions = new FixedQuotas(amount, size);
        return new OpenVault(
                repository, new VaultAmountQuota(permissions, 0), new VaultSizeQuota(permissions, 1), CLOCK);
    }

    /** A {@code Permissions} that returns a fixed amount/size for the two vault families and denies plain nodes. */
    private record FixedQuotas(int amount, int size) implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            if (family.node().equals(VaultAmountQuota.FAMILY.node())) {
                return QuotaResult.limited(amount);
            }
            if (family.node().equals(VaultSizeQuota.FAMILY.node())) {
                return QuotaResult.limited(size);
            }
            return QuotaResult.limited(configDefault);
        }
    }

    /** An in-memory {@code VaultRepository} keyed by {@code (owner, index)}. */
    private static final class FakeRepository implements VaultRepository {
        private final Map<VaultId, Vault> rows = new ConcurrentHashMap<>();

        @Override
        public Optional<Vault> find(VaultId id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            List<Integer> indices = new ArrayList<>();
            rows.keySet().stream()
                    .filter(id -> id.owner().equals(owner.uuid()))
                    .map(VaultId::index)
                    .sorted()
                    .forEach(indices::add);
            return indices;
        }

        @Override
        public int count(PlayerRef owner) {
            return (int) rows.keySet().stream()
                    .filter(id -> id.owner().equals(owner.uuid()))
                    .count();
        }

        @Override
        public void save(Vault vault) {
            rows.put(vault.id(), vault);
        }
    }
}

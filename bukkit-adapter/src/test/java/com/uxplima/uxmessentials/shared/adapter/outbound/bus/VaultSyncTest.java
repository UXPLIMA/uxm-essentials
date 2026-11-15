package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.vaults.CachedVaultRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.VaultChanged;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultPayment;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import com.uxplima.uxmessentials.vaults.domain.VaultStamp;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The cached, broadcasting vault store the plugin runs on reports what the store underneath did, so a lost race is
 * never mistaken for a win.
 */
class VaultSyncTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final VaultId FIRST = VaultId.of(ALEX, 1);
    private static final VaultPayment FIVE = new VaultPayment(new BigDecimal("5"), "coins");

    private final Rows rows = new Rows();
    private final Frames frames = new Frames();
    private final VaultRepository store = VaultSync.repository(new CachedVaultRepository(rows), frames);

    @Test
    void aDeleteReportsWhetherItRemovedTheVault() {
        store.create(Vault.allocate(FIRST, new VaultSize(1), NOW, FIVE));

        assertThat(store.delete(FIRST, Optional.empty())).isFalse();
        assertThat(store.delete(FIRST, Optional.of(FIVE))).isTrue();
        assertThat(store.find(FIRST)).isEmpty();
        assertThat(store.delete(FIRST, Optional.of(FIVE))).isFalse();
    }

    @Test
    void createAndUpdateReportWhetherTheyWrote() {
        assertThat(store.writeContents(Vault.allocate(FIRST, new VaultSize(1), NOW))
                        .isPresent())
                .isFalse();
        assertThat(store.create(Vault.allocate(FIRST, new VaultSize(1), NOW, FIVE)))
                .isTrue();
        assertThat(store.create(Vault.allocate(FIRST, new VaultSize(1), NOW))).isFalse();
        assertThat(store.writeContents(Vault.allocate(FIRST, new VaultSize(2), NOW))
                        .isPresent())
                .isTrue();

        assertThat(frames.published).allSatisfy(frame -> assertThat(frame).isInstanceOf(VaultChanged.class));
    }

    @Test
    void theCachedCopyTakesTheItemsAClosingWindowWrote() {
        store.create(Vault.allocate(FIRST, new VaultSize(1), NOW, FIVE));
        Vault opened = store.find(FIRST).orElseThrow();
        store.update(opened.renamedTo("loot"), VaultRepository.Columns.PRESENTATION);
        Vault named = store.find(FIRST).orElseThrow();

        VaultStamp written = store.writeContents(
                        opened.withContents(ALEX, VaultContents.of(new byte[] {5}), NOW.plusSeconds(1))
                                .result())
                .orElseThrow();
        rows.held.clear();

        Vault cached = store.find(FIRST).orElseThrow();
        assertThat(named.displayName()).isEqualTo("loot");
        assertThat(cached.contents()).isEqualTo(VaultContents.of(new byte[] {5}));
        assertThat(cached.displayName()).isEqualTo("loot");
        assertThat(cached.lastTouched()).isEqualTo(NOW.plusSeconds(1));
        assertThat(cached.stamp()).isEqualTo(written);
        assertThat(written.version()).isEqualTo(opened.stamp().version() + 1);
    }

    @Test
    void aPeersChangeToAVaultIsPassedOnAfterTheCacheDropsIt() {
        List<VaultId> forgotten = new ArrayList<>();
        CachedVaultRepository cache = new CachedVaultRepository(rows);
        RemoteSyncListener listener = VaultSync.listener(cache, forgotten::add);

        listener.onRemoteChange(new VaultChanged("survival", ALEX.uuid(), 1));

        assertThat(forgotten).containsExactly(FIRST);
    }

    /** Rows keyed by vault, with the store's payment rules: only a create records one, a delete must match it. */
    private static final class Rows implements VaultRepository {

        private final Map<VaultId, Vault> held = new HashMap<>();

        @Override
        public Optional<Vault> find(VaultId id) {
            return Optional.ofNullable(held.get(id));
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            return List.of();
        }

        @Override
        public List<VaultSummary> summaries(PlayerRef owner) {
            return List.of();
        }

        @Override
        public int count(PlayerRef owner) {
            return held.size();
        }

        @Override
        public VaultStamp save(Vault vault, String writer) {
            held.put(vault.id(), vault);
            return vault.stamp();
        }

        @Override
        public Optional<VaultStamp> writeContents(Vault vault, String writer, @Nullable VaultStamp onlyAt) {
            Vault stored = held.get(vault.id());
            if (stored == null || (onlyAt != null && !stored.stamp().equals(onlyAt))) {
                return Optional.empty();
            }
            VaultStamp stamp =
                    new VaultStamp(stored.stamp().lineage(), stored.stamp().version() + 1);
            held.put(vault.id(), stored.withContentsOf(vault, stamp));
            return Optional.of(stamp);
        }

        @Override
        public Optional<LastWrite> lastWrite(VaultId id) {
            return find(id).map(stored -> new LastWrite(stored.stamp(), null));
        }

        @Override
        public boolean update(Vault vault, Columns columns) {
            return held.computeIfPresent(vault.id(), (id, stored) -> switch (columns) {
                        case PRESENTATION ->
                            stored.renamedTo(vault.displayName()).iconSet(vault.iconMaterial());
                        case SIZE -> stored.openedAt(vault.size());
                    })
                    != null;
        }

        @Override
        public boolean create(Vault vault) {
            return held.putIfAbsent(vault.id(), vault) == null;
        }

        @Override
        public boolean delete(VaultId id, Optional<VaultPayment> expected) {
            Vault stored = held.get(id);
            if (stored == null || !stored.paid().equals(expected)) {
                return false;
            }
            held.remove(id);
            return true;
        }

        @Override
        public int deleteUntouchedBefore(Instant cutoff) {
            return 0;
        }
    }

    /** Every frame published, from one backend. */
    private static final class Frames implements BusPublisher {

        private final List<NetworkMessage> published = new ArrayList<>();

        @Override
        public void publish(NetworkMessage message) {
            published.add(message);
        }

        @Override
        public String serverId() {
            return "lobby";
        }
    }
}

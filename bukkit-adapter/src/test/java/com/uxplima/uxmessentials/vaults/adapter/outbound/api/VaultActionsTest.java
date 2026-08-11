package com.uxplima.uxmessentials.vaults.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.application.DeleteVault;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.RenameVault;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.SetVaultIcon;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultCharge;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.application.VaultSizeQuota;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultItemPolicy;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The published vault writes: they run the same use cases the command does, the quota still gates a vault nobody
 * has opened yet, and the open puts the real window in front of the owner rather than handing anybody the items.
 */
class VaultActionsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC);

    private ServerMock server;
    private PlayerMock alice;
    private PlayerRef owner;
    private FakeVaultRepository repository;
    private ActionDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        alice = server.addPlayer("Alice");
        owner = new PlayerRef(alice.getUniqueId(), alice.getName());
        repository = new FakeVaultRepository();
        scheduler = new ActionDoubles.InlineScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openAllocatesTheVaultOnAWorkerAndShowsItOnTheOwnersOwnThread() {
        UxmOutcome outcome = actions().open(owner.uuid(), 1).join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(repository.find(VaultId.of(owner, 1))).isPresent();
        assertThat(scheduler.asyncCalls()).isOne();
        assertThat(scheduler.entityCalls()).isOne();
    }

    @Test
    void openingForSomebodyWhoIsNotHereIsRefusedWithoutReadingAnything() {
        UxmOutcome outcome = actions().open(UUID.randomUUID(), 1).join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.PLAYER_OFFLINE);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    @Test
    void aVaultPastTheQuotaIsRefusedAndNothingIsWritten() {
        UxmOutcome outcome = actions().open(owner.uuid(), 9).join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.REFUSED);
        assertThat(repository.find(VaultId.of(owner, 9))).isEmpty();
    }

    @Test
    void deletingAVaultThatIsNotThereIsNotFound() {
        UxmOutcome outcome = actions().delete(owner.uuid(), 2).join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.NOT_FOUND);
    }

    @Test
    void deleteRemovesTheRowAndFreesTheSlot() {
        actions().open(owner.uuid(), 1).join();

        assertThat(actions().delete(owner.uuid(), 1).join().succeeded()).isTrue();
        assertThat(repository.find(VaultId.of(owner, 1))).isEmpty();
    }

    @Test
    void renameSetsTheLabelAndClearNameTakesItBackOff() {
        actions().open(owner.uuid(), 1).join();

        assertThat(actions().rename(owner.uuid(), 1, "Ores").join().succeeded()).isTrue();
        assertThat(stored(1).displayName()).isEqualTo("Ores");

        assertThat(actions().clearName(owner.uuid(), 1).join().succeeded()).isTrue();
        assertThat(stored(1).displayName()).isNull();
    }

    @Test
    void theIconIsNormalisedToTheRealMaterialAndAnUnknownOneIsRefused() {
        actions().open(owner.uuid(), 1).join();

        assertThat(actions().setIcon(owner.uuid(), 1, "diamond_ore").join().succeeded())
                .isTrue();
        assertThat(stored(1).iconMaterial()).isEqualTo("DIAMOND_ORE");

        UxmOutcome unknown = actions().setIcon(owner.uuid(), 1, "not_a_block").join();
        assertThat(unknown.failureOrThrow().code()).isEqualTo(UxmFailure.REFUSED);
        assertThat(stored(1).iconMaterial()).isEqualTo("DIAMOND_ORE");

        assertThat(actions().clearIcon(owner.uuid(), 1).join().succeeded()).isTrue();
        assertThat(stored(1).iconMaterial()).isNull();
    }

    @Test
    void anIconIsRefusedOutrightWhenTheOperatorSwitchedCustomIconsOff() {
        actions().open(owner.uuid(), 1).join();

        UxmOutcome outcome =
                actions(false).setIcon(owner.uuid(), 1, "DIAMOND_ORE").join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.REFUSED);
        assertThat(stored(1).iconMaterial()).isNull();
    }

    @Test
    void vaultNumbersCountFromOne() {
        assertThatThrownBy(() -> actions().rename(owner.uuid(), 0, "Ores"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Vault stored(int index) {
        return repository.find(VaultId.of(owner, index)).orElseThrow();
    }

    private VaultActions actions() {
        return actions(true);
    }

    private VaultActions actions(boolean allowCustomIcon) {
        Permissions permissions = new ThreeVaults();
        VaultAmountQuota amounts = new VaultAmountQuota(permissions, 3);
        VaultSizeQuota sizes = new VaultSizeQuota(permissions, 6);
        VaultNotifier notifier = new VaultNotifier(new KeyMessages(), new NullSink());
        VaultCharge charge = new VaultCharge(permissions, Optional.empty(), VaultChargeSettings.allFree());
        VaultView view = new VaultView(
                new KeyMessages(),
                new NullSink(),
                new SaveVault(repository, event -> {}, CLOCK),
                scheduler,
                permissions,
                VaultItemPolicy.allowAll(),
                null);
        return new VaultActions(
                new OpenVault(repository, amounts, sizes, charge, CLOCK),
                new DeleteVault(repository, charge, new NoAudit(), notifier),
                new RenameVault(repository, notifier),
                new SetVaultIcon(repository, notifier),
                view,
                allowCustomIcon,
                new QueryDoubles.MapLookup().with(owner),
                scheduler);
    }

    /** Three vaults of six rows each, which is the shipped default. */
    private static final class ThreeVaults implements Permissions {

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NullSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoAudit implements VaultAudit {
        @Override
        public void adminOpened(PlayerRef actor, PlayerRef owner, UUID ownerId, int index) {}

        @Override
        public void adminDeleted(PlayerRef actor, PlayerRef owner, UUID ownerId, int index) {}

        @Override
        public void purged(int count) {}
    }

    /** Keeps whole vaults in a map, which is what the writes need and the query fake deliberately does not do. */
    private static final class FakeVaultRepository implements VaultRepository {

        private final Map<VaultId, Vault> vaults = new LinkedHashMap<>();

        @Override
        public Optional<Vault> find(VaultId id) {
            return Optional.ofNullable(vaults.get(id));
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            return vaults.keySet().stream()
                    .filter(id -> id.owner().equals(owner.uuid()))
                    .map(VaultId::index)
                    .toList();
        }

        @Override
        public List<VaultSummary> summaries(PlayerRef owner) {
            List<VaultSummary> found = new ArrayList<>();
            vaults.forEach((id, vault) -> {
                if (id.owner().equals(owner.uuid())) {
                    found.add(new VaultSummary(id.index(), vault.displayName(), vault.iconMaterial()));
                }
            });
            return List.copyOf(found);
        }

        @Override
        public int count(PlayerRef owner) {
            return ownedIndices(owner).size();
        }

        @Override
        public void save(Vault vault) {
            vaults.put(vault.id(), vault);
        }

        @Override
        public void delete(VaultId id) {
            vaults.remove(id);
        }

        @Override
        public int deleteUntouchedBefore(Instant cutoff) {
            return 0;
        }
    }
}

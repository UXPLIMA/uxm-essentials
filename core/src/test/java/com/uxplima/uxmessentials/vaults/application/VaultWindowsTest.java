package com.uxplima.uxmessentials.vaults.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import org.junit.jupiter.api.Test;

/**
 * Vault windows hold each vault's items in exactly one place: an open window, a write on its way, or the store. A
 * reopen before a close's write has landed sees the closed window's items, a re-open takes the old window's items
 * over, and a window whose vault was deleted gives its items back once instead of bringing the vault back.
 */
class VaultWindowsTest {

    private static final VaultChargeSettings CREATE_5_REFUND_5 =
            new VaultChargeSettings(new BigDecimal("5"), BigDecimal.ZERO, new BigDecimal("5"));
    private static final VaultContents X = VaultContents.of(new byte[] {42});
    private static final VaultContents Y = VaultContents.of(new byte[] {7});
    private static final VaultId SECOND = VaultId.of(ALEX, 2);

    private final MutableClock clock = new MutableClock(CLOCK.instant());
    private final InMemoryVaults vaults = new InMemoryVaults();
    private final FakePermissions permissions = new FakePermissions();
    private final VaultPurse purse = new VaultPurse("100");
    private final VaultCharge charge = new VaultCharge(permissions, Optional.of(purse), CREATE_5_REFUND_5);
    private final VaultNotifier notifier = new VaultNotifier((viewer, key, placeholders) -> key.key(), (v, t) -> {});
    private final SaveVault saveVault = new SaveVault(vaults, new RecordingEvents(), clock);
    private final List<String> handedBack = new ArrayList<>();
    private final VaultWindows<Chest> windows =
            new VaultWindows<>(saveVault, (owner, items) -> handedBack.add(owner.name() + ":" + bytes(items)), clock);
    private final OpenVault openVault = new OpenVault(
            vaults, new VaultAmountQuota(permissions, 10), new VaultSizeQuota(permissions, 1), charge, clock);
    private final DeleteVault deleteVault = new DeleteVault(vaults, charge, new NoAudit(), notifier);
    /** Writes queued for the async lane, run when a test says so. */
    private final List<Runnable> writes = new ArrayList<>();

    /** A window: the items a player sees in it. */
    private static final class Chest {
        private VaultContents items = VaultContents.empty();
    }

    private static String bytes(VaultContents items) {
        return items.payload().map(b -> Byte.toString(b[0])).orElse("none");
    }

    /** Open ALEX's {@code vault} for {@code viewer} in a new window the way the GUI does; empty when refused. */
    private Optional<Chest> tryShow(PlayerRef viewer, Vault vault) {
        Chest chest = new Chest();
        Optional<VaultContents> seed = windows.claim(viewer, ALEX, vault, chest, old -> {
            VaultContents live = old.items;
            old.items = VaultContents.empty();
            return live;
        });
        seed.ifPresent(items -> chest.items = items);
        return seed.map(items -> chest);
    }

    private Chest show(PlayerRef viewer, Vault vault) {
        return tryShow(viewer, vault).orElseThrow();
    }

    /** Close {@code chest} the way the GUI's close handler does, queueing its write. */
    private void close(VaultId id, Chest chest) {
        windows.release(id, chest, chest.items).ifPresent(writes::add);
        clock.advance(java.time.Duration.ofMillis(5));
    }

    private void runWrites() {
        List<Runnable> queued = List.copyOf(writes);
        writes.clear();
        queued.forEach(Runnable::run);
    }

    private Vault stored(VaultId id) {
        return vaults.find(id).orElseThrow();
    }

    /** ALEX buys vault 2 and it ends up holding {@code items}, written and settled. */
    private void vaultHolding(VaultContents items) {
        Chest chest = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        chest.items = items;
        close(SECOND, chest);
        runWrites();
    }

    @Test
    void aReopenBeforeTheCloseWasWrittenShowsWhatTheWindowClosedWith() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        a.items = VaultContents.empty(); // X taken into the inventory

        close(SECOND, a);
        Vault cached = openVault.open(ALEX, 2).orElseThrow(); // read before the write lands
        Chest b = show(ALEX, cached);
        runWrites();
        close(SECOND, b);
        runWrites();

        assertThat(cached.contents()).isEqualTo(X);
        assertThat(b.items).isEqualTo(VaultContents.empty());
        assertThat(stored(SECOND).contents()).isEqualTo(VaultContents.empty());
    }

    @Test
    void aReopenWhoseReadPredatesALandedWriteShowsWhatWasWritten() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        a.items = Y;
        close(SECOND, a);
        Vault readBefore = openVault.open(ALEX, 2).orElseThrow();
        runWrites();

        Chest b = show(ALEX, readBefore);

        assertThat(readBefore.contents()).isEqualTo(X);
        assertThat(b.items).isEqualTo(Y);
    }

    @Test
    void aReopenReadAfterTheWriteLandedShowsTheStoredItems() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        a.items = Y;
        close(SECOND, a);
        runWrites();

        Chest b = show(ALEX, openVault.open(ALEX, 2).orElseThrow());

        assertThat(b.items).isEqualTo(Y);
    }

    @Test
    void anOlderWriteThatRunsLastNeverOverwritesANewerOne() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        close(SECOND, a);
        Chest b = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        b.items = Y;
        close(SECOND, b);

        List<Runnable> queued = List.copyOf(writes);
        writes.clear();
        queued.get(1).run();
        queued.get(0).run();

        assertThat(stored(SECOND).contents()).isEqualTo(Y);
    }

    @Test
    void aVaultDeletedBeforeAClosedWindowWasWrittenGivesItsItemsBackOnceEvenWhenReopened() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        Vault cached = openVault.open(ALEX, 2).orElseThrow();
        close(SECOND, a);
        assertThat(deleteVault.delete(ALEX, 2).isOk()).isTrue();

        Chest b = show(ALEX, cached);
        runWrites();
        assertThat(handedBack).isEmpty();
        close(SECOND, b);
        runWrites();

        assertThat(b.items).isEqualTo(X);
        assertThat(handedBack).containsExactly("Alex:42");
        assertThat(vaults.find(SECOND)).isEmpty();
    }

    @Test
    void aClosedWindowWhoseVaultWasDeletedGivesItsItemsBackAndAStaleReopenIsEmpty() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        Vault cached = openVault.open(ALEX, 2).orElseThrow();
        deleteVault.delete(ALEX, 2);
        close(SECOND, a);
        runWrites();

        Chest b = show(ALEX, cached);

        assertThat(handedBack).containsExactly("Alex:42");
        assertThat(b.items).isEqualTo(VaultContents.empty());
    }

    @Test
    void reopeningAVaultDeletedInTheSameTickGivesItsItemsBackOnce() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        Vault stale = openVault.open(ALEX, 2).orElseThrow();
        assertThat(deleteVault.delete(ALEX, 2).isOk()).isTrue();

        Chest b = show(ALEX, stale);
        close(SECOND, a);
        close(SECOND, b);
        runWrites();

        assertThat(b.items).isEqualTo(X);
        assertThat(a.items).isEqualTo(VaultContents.empty());
        assertThat(handedBack).containsExactly("Alex:42");
        assertThat(vaults.find(SECOND)).isEmpty();
    }

    @Test
    void aWriteOfAWindowThatStaysOpenNeverHandsItsItemsBack() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        deleteVault.delete(ALEX, 2);

        windows.checkpoint(SECOND, a, a.items).ifPresent(Runnable::run);
        assertThat(handedBack).isEmpty();
        close(SECOND, a);
        runWrites();

        assertThat(handedBack).containsExactly("Alex:42");
    }

    @Test
    void aClosingWindowLeavesANameSetWhileItWasOpen() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        new RenameVault(vaults, notifier).rename(ALEX, 2, "loot");
        a.items = Y;

        close(SECOND, a);
        runWrites();

        assertThat(stored(SECOND).displayName()).isEqualTo("loot");
        assertThat(stored(SECOND).contents()).isEqualTo(Y);
    }

    @Test
    void anotherViewerIsRefusedWhileTheVaultIsOpen() {
        Vault bought = openVault.open(ALEX, 1).orElseThrow();
        assertThat(tryShow(ALEX, bought)).isPresent();

        assertThat(tryShow(SAM, bought)).isEmpty();
        assertThat(windows.count()).isEqualTo(1);
    }

    @Test
    void aTakenOverWindowIsNotWrittenOnItsOwn() {
        Vault bought = openVault.open(ALEX, 1).orElseThrow();
        Chest a = show(ALEX, bought);
        a.items = X;

        Chest b = show(ALEX, openVault.open(ALEX, 1).orElseThrow());
        close(VaultId.of(ALEX, 1), a);
        assertThat(writes).isEmpty();
        close(VaultId.of(ALEX, 1), b);
        runWrites();

        assertThat(b.items).isEqualTo(X);
        assertThat(stored(VaultId.of(ALEX, 1)).contents()).isEqualTo(X);
        assertThat(handedBack).isEmpty();
    }

    @Test
    void everyHeldWindowIsListedForTheModuleStop() {
        Chest a = show(ALEX, openVault.open(ALEX, 1).orElseThrow());
        a.items = X;
        deleteVault.delete(ALEX, 1);

        for (VaultWindows.Window<Chest> window : windows.held()) {
            windows.release(window.vault().id(), window.window(), window.window().items)
                    .ifPresent(Runnable::run);
        }

        assertThat(handedBack).containsExactly("Alex:42");
        assertThat(windows.count()).isZero();
    }

    @Test
    void aStaffWindowKeepsItsItemsInTheVaultEvenAfterADelete() {
        Vault bought = openVault.open(ALEX, 1).orElseThrow();
        Chest staff = show(SAM, bought);
        staff.items = X;
        deleteVault.delete(ALEX, 1);

        close(VaultId.of(ALEX, 1), staff);
        runWrites();

        assertThat(handedBack).isEmpty();
        Vault kept = stored(VaultId.of(ALEX, 1));
        assertThat(kept.contents()).isEqualTo(X);
        assertThat(kept.paid()).isEmpty();
    }

    /** The audit lines are not what these tests look at. */
    private static final class NoAudit implements VaultAudit {

        @Override
        public void adminOpened(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {}

        @Override
        public void adminDeleted(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {}

        @Override
        public void purged(int count) {}
    }
}

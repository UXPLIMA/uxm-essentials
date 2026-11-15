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
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
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
    private final InterleavedVaults store = new InterleavedVaults(vaults);
    private final FakePermissions permissions = new FakePermissions();
    private final VaultPurse purse = new VaultPurse("100");
    private final VaultCharge charge = new VaultCharge(permissions, Optional.of(purse), CREATE_5_REFUND_5);
    private final VaultNotifier notifier = new VaultNotifier((viewer, key, placeholders) -> key.key(), (v, t) -> {});
    private final SaveVault saveVault = new SaveVault(store, new RecordingEvents());
    private final QuietLog log = new QuietLog();
    private final List<String> handedBack = new ArrayList<>();
    private final QueuedScheduler retries = new QueuedScheduler();
    private final VaultWindows<Chest> windows = new VaultWindows<>(
            saveVault,
            store,
            (owner, items) -> handedBack.add(owner.name() + ":" + bytes(items)),
            retries,
            () -> true,
            clock,
            log);
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
    void aServerWhoseClockIsAheadStillSeesTheWriteAnotherServerMadeAfterIt() {
        vaultHolding(X);
        // The survival server's clock is five seconds behind this one, and it shares the same store.
        MutableClock behind = new MutableClock(clock.instant().minusSeconds(5));
        VaultWindows<Chest> survival = new VaultWindows<>(
                new SaveVault(vaults, new RecordingEvents()),
                vaults,
                (owner, items) -> {},
                retries,
                () -> true,
                behind,
                log);
        OpenVault survivalOpen = new OpenVault(
                vaults, new VaultAmountQuota(permissions, 10), new VaultSizeQuota(permissions, 1), charge, behind);

        // Here: the window closes with the diamonds still in it, and the write lands.
        Chest here = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        close(SECOND, here);
        runWrites();
        // On survival, two seconds later by its own clock: the diamonds are taken out and the window closes.
        behind.advance(java.time.Duration.ofSeconds(2));
        Chest there = new Chest();
        there.items = survival.claim(ALEX, ALEX, survivalOpen.open(ALEX, 2).orElseThrow(), there, old -> old.items)
                .orElseThrow();
        assertThat(there.items).isEqualTo(X);
        there.items = VaultContents.empty();
        survival.release(SECOND, there, there.items).orElseThrow().run();

        // Back here within the minute: the store's copy is newer than this server's write, whatever the clocks say.
        Chest again = show(ALEX, openVault.open(ALEX, 2).orElseThrow());

        assertThat(stored(SECOND).lastTouched()).isBefore(clock.instant());
        assertThat(again.items).isEqualTo(VaultContents.empty());
    }

    @Test
    void aWriteThatFailsOnceIsRetriedAndLandsWithoutHandingAnythingBack() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        a.items = Y;
        store.failNextWrite();

        close(SECOND, a);
        runWrites();
        assertThat(stored(SECOND).contents()).isEqualTo(X);
        Chest meanwhile = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        close(SECOND, meanwhile);
        retries.runAll();
        runWrites();

        assertThat(meanwhile.items).isEqualTo(Y);
        assertThat(handedBack).isEmpty();
        assertThat(stored(SECOND).contents()).isEqualTo(Y);
    }

    @Test
    void aFailedWriteIsRetriedUntilItLands() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        a.items = Y;
        store.failWrites(2);

        close(SECOND, a);
        runWrites();
        retries.runAll();

        assertThat(handedBack).isEmpty();
        assertThat(stored(SECOND).contents()).isEqualTo(Y);
        assertThat(windows.awaitIdle(java.time.Duration.ZERO)).isTrue();
    }

    @Test
    void aWriteAppliedWithoutAnAnswerIsRecognisedByItsWriterToken() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        a.items = Y;
        long before = stored(SECOND).stamp().version();
        store.failNextWriteAfterCommit();

        close(SECOND, a);
        runWrites();
        retries.runAll();

        assertThat(stored(SECOND).stamp().version()).isEqualTo(before + 1);
        assertThat(stored(SECOND).contents()).isEqualTo(Y);
        assertThat(handedBack).isEmpty();
        assertThat(show(ALEX, openVault.open(ALEX, 2).orElseThrow()).items).isEqualTo(Y);
    }

    /**
     * Another server on the same rows, with its own windows and no memory of this server's writes. Its connection
     * is its own, so the failures armed on this server's store never reach it.
     */
    private VaultWindows<Chest> anotherServer() {
        return anotherServer(new QuietLog());
    }

    private VaultWindows<Chest> anotherServer(QuietLog itsLog) {
        return new VaultWindows<>(
                new SaveVault(vaults, new RecordingEvents()),
                vaults,
                (owner, items) -> handedBack.add("there " + owner.name() + ":" + bytes(items)),
                new QueuedScheduler(),
                () -> true,
                clock,
                itsLog);
    }

    /** SAM opens ALEX's vault 2 as staff on {@code server}, from the row as it is now. */
    private Chest staffOn(VaultWindows<Chest> server) {
        Chest staff = new Chest();
        staff.items =
                server.claim(SAM, ALEX, stored(SECOND), staff, old -> old.items).orElseThrow();
        return staff;
    }

    /** Open, change and close ALEX's vault 2 on {@code server}, writing {@code items} there. */
    private void editOn(VaultWindows<Chest> server, VaultContents expected, VaultContents items) {
        Chest there = new Chest();
        there.items = server.claim(ALEX, ALEX, openVault.open(ALEX, 2).orElseThrow(), there, old -> old.items)
                .orElseThrow();
        assertThat(there.items).isEqualTo(expected);
        there.items = items;
        server.release(SECOND, there, there.items).orElseThrow().run();
    }

    @Test
    void aRetryNeverWritesOverWhatAnotherServerWroteAfterTheWriteFailed() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        a.items = Y; // X taken out, Y put in on this server
        store.failNextWrite();
        close(SECOND, a);
        runWrites();

        // Before the retry, the player opens the vault on another server, still holding X, and empties it.
        editOn(anotherServer(), X, VaultContents.empty());
        retries.runAll();

        assertThat(stored(SECOND).contents()).isEqualTo(VaultContents.empty());
        assertThat(handedBack).isEmpty();
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("two servers edited"));
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("vault_items_unwritten"));
        assertThat(windows.unsettled()).containsExactly(ALEX.uuid() + "#2 (not written)");
        assertThat(windows.awaitIdle(java.time.Duration.ZERO)).isTrue();
        assertThat(show(ALEX, openVault.open(ALEX, 2).orElseThrow()).items).isEqualTo(Y);
    }

    @Test
    void aWriteAnotherServerMadeWhileThisOneFailedIsNotTakenForThisOne() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        a.items = Y;
        VaultWindows<Chest> survival = anotherServer();
        // The other server's write lands at the moment this server's write is lost.
        store.beforeNextWrite(() -> editOn(survival, X, VaultContents.empty()));
        store.failNextWrite();

        close(SECOND, a);
        runWrites();
        retries.runAll();

        assertThat(stored(SECOND).contents()).isEqualTo(VaultContents.empty());
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("two servers edited"));
        assertThat(windows.unsettled()).containsExactly(ALEX.uuid() + "#2 (not written)");
    }

    @Test
    void aRetryOfAStoppedInstanceNeverWritesOverTheReloadedInstancesWrite() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        a.items = Y;
        store.failNextWrite();
        close(SECOND, a);
        runWrites();
        assertThat(windows.awaitIdle(java.time.Duration.ZERO)).isFalse(); // the stop gives up waiting on the retry

        // The module comes back as a new instance, which reads the row and writes the player's next close.
        editOn(anotherServer(), X, VaultContents.of(new byte[] {9}));
        retries.runAll(); // the old instance's retry fires after the reload

        assertThat(stored(SECOND).contents()).isEqualTo(VaultContents.of(new byte[] {9}));
        assertThat(handedBack).isEmpty();
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("two servers edited"));
    }

    @Test
    void theOwnersCloseNeverWritesOverAStaffWriteFromAnotherServerWhileItsWindowWasOpen() {
        vaultHolding(X);
        Chest owner = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        VaultWindows<Chest> survival = anotherServer();
        Chest staff = staffOn(survival);
        owner.items = Y; // X taken out, Y put in by the owner here
        staff.items = VaultContents.of(new byte[] {9}); // X taken out, 9 put in by staff there

        survival.release(SECOND, staff, staff.items).orElseThrow().run();
        close(SECOND, owner);
        runWrites();

        assertThat(stored(SECOND).contents()).isEqualTo(VaultContents.of(new byte[] {9}));
        assertThat(handedBack).isEmpty();
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("two servers edited"));
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("vault_items_unwritten"));
        assertThat(windows.unsettled()).containsExactly(ALEX.uuid() + "#2 (not written)");
        assertThat(show(ALEX, openVault.open(ALEX, 2).orElseThrow()).items).isEqualTo(Y);
    }

    @Test
    void aStaffCloseNeverWritesOverTheOwnersWriteFromAnotherServerWhileItsWindowWasOpen() {
        vaultHolding(X);
        QuietLog survivalLog = new QuietLog();
        VaultWindows<Chest> survival = anotherServer(survivalLog);
        Chest staff = staffOn(survival);
        Chest owner = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        owner.items = Y;
        staff.items = VaultContents.of(new byte[] {9});

        close(SECOND, owner);
        runWrites();
        survival.release(SECOND, staff, staff.items).orElseThrow().run();

        assertThat(stored(SECOND).contents()).isEqualTo(Y);
        assertThat(handedBack).isEmpty();
        assertThat(survivalLog.warnings()).anySatisfy(line -> assertThat(line).contains("two servers edited"));
        assertThat(survival.unsettled()).containsExactly(ALEX.uuid() + "#2 (not written)");
    }

    @Test
    void aWriteThatFailsWhileTheVaultIsGoneHandsTheItemsBackOnce() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        a.items = Y;
        deleteVault.delete(ALEX, 2);
        store.failNextWrite();

        close(SECOND, a);
        runWrites();
        retries.runAll();

        assertThat(handedBack).containsExactly("Alex:7");
        assertThat(vaults.find(SECOND)).isEmpty();
    }

    @Test
    void whenEveryRetryFailsTheItemsStayForAReopenAndGoToTheLog() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        a.items = Y;
        store.failWrites(100);

        close(SECOND, a);
        runWrites();
        retries.runAll();

        assertThat(handedBack).isEmpty();
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("vault_items_unwritten"));
        assertThat(windows.unsettled()).containsExactly(ALEX.uuid() + "#2 (not written)");
        assertThat(windows.awaitIdle(java.time.Duration.ZERO)).isTrue();
        assertThat(show(ALEX, openVault.open(ALEX, 2).orElseThrow()).items).isEqualTo(Y);
    }

    @Test
    void theStopWaitsUntilEveryWindowIsClosedAndEveryWriteHasLanded() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        assertThat(windows.awaitIdle(java.time.Duration.ZERO)).isFalse();

        close(SECOND, a);
        assertThat(windows.awaitIdle(java.time.Duration.ZERO)).isFalse();
        runWrites();

        assertThat(windows.awaitIdle(java.time.Duration.ZERO)).isTrue();
    }

    @Test
    void aPeersChangeDropsALandedWriteButNeverOneStillOnItsWay() {
        vaultHolding(X);
        Chest a = show(ALEX, openVault.open(ALEX, 2).orElseThrow());
        a.items = Y;
        Vault readBefore = openVault.open(ALEX, 2).orElseThrow();
        close(SECOND, a);

        windows.forget(SECOND);
        Chest b = show(ALEX, readBefore);

        assertThat(b.items).isEqualTo(Y);
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

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
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import org.junit.jupiter.api.Test;

/**
 * Vault windows hold each vault's items in exactly one place: a re-open takes the old window's items over, and a
 * window whose vault was deleted gives its items back once instead of bringing the vault back.
 */
class VaultWindowsTest {

    private static final VaultChargeSettings CREATE_5_REFUND_5 =
            new VaultChargeSettings(new BigDecimal("5"), BigDecimal.ZERO, new BigDecimal("5"));
    private static final VaultContents X = VaultContents.of(new byte[] {42});

    private final InMemoryVaults vaults = new InMemoryVaults();
    private final FakePermissions permissions = new FakePermissions();
    private final VaultPurse purse = new VaultPurse("100");
    private final VaultCharge charge = new VaultCharge(permissions, Optional.of(purse), CREATE_5_REFUND_5);
    private final VaultNotifier notifier = new VaultNotifier((viewer, key, placeholders) -> key.key(), (v, t) -> {});
    private final SaveVault saveVault = new SaveVault(vaults, new RecordingEvents(), CLOCK);
    private final List<String> handedBack = new ArrayList<>();
    private final CloseVaultWindow closeWindow = new CloseVaultWindow(
            saveVault,
            (owner, items) -> handedBack.add(
                    owner.name() + ":" + items.payload().map(b -> b[0]).orElse(null)));
    private final VaultWindows<Chest> windows = new VaultWindows<>();
    private final OpenVault openVault = new OpenVault(
            vaults, new VaultAmountQuota(permissions, 10), new VaultSizeQuota(permissions, 1), charge, CLOCK);
    private final DeleteVault deleteVault = new DeleteVault(vaults, charge, new NoAudit(), notifier);

    /** A window: the items a player sees in it. */
    private static final class Chest {
        private VaultContents items = VaultContents.empty();
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

    /** Close {@code chest} the way the GUI's close handler does. */
    private void close(Vault vault, Chest chest) {
        windows.release(vault.id(), chest)
                .ifPresent(window -> closeWindow.close(window.viewer(), window.owner(), window.vault(), chest.items));
    }

    @Test
    void reopeningAVaultDeletedInTheSameTickGivesItsItemsBackOnce() {
        Vault bought = openVault.open(ALEX, 2).orElseThrow();
        saveVault.save(ALEX, bought, X);
        Vault first = openVault.open(ALEX, 2).orElseThrow();
        Chest a = show(ALEX, first);
        // "/vault 2" reads the vault, then "/vault delete 2" lands before the new window is shown.
        Vault stale = openVault.open(ALEX, 2).orElseThrow();
        assertThat(deleteVault.delete(ALEX, 2).isOk()).isTrue();

        Chest b = show(ALEX, stale);
        close(stale, a);
        close(stale, b);

        assertThat(b.items).isEqualTo(X);
        assertThat(a.items).isEqualTo(VaultContents.empty());
        assertThat(handedBack).containsExactly("Alex:42");
        assertThat(vaults.find(VaultId.of(ALEX, 2))).isEmpty();
        assertThat(purse.moves()).containsExactly("-5", "+5 coins");
    }

    @Test
    void reopeningAnExistingVaultWritesOnlyTheWindowThatFinallyCloses() {
        Vault bought = openVault.open(ALEX, 1).orElseThrow();
        Chest a = show(ALEX, bought);
        a.items = X;

        Chest b = show(ALEX, openVault.open(ALEX, 1).orElseThrow());
        int savesBeforeClose = vaults.saves();
        close(bought, a);
        assertThat(vaults.saves()).isEqualTo(savesBeforeClose);
        close(bought, b);

        assertThat(b.items).isEqualTo(X);
        assertThat(vaults.find(VaultId.of(ALEX, 1)).orElseThrow().contents()).isEqualTo(X);
        assertThat(handedBack).isEmpty();
    }

    @Test
    void anotherViewerIsRefusedWhileTheVaultIsOpen() {
        Vault bought = openVault.open(ALEX, 1).orElseThrow();
        assertThat(tryShow(ALEX, bought)).isPresent();

        assertThat(tryShow(SAM, bought)).isEmpty();
        assertThat(windows.count()).isEqualTo(1);
    }

    @Test
    void aWindowStillOpenWhenTheModuleStopsGivesItsItemsBackIfTheVaultIsGone() {
        Vault bought = openVault.open(ALEX, 1).orElseThrow();
        Chest a = show(ALEX, bought);
        a.items = X;
        deleteVault.delete(ALEX, 1);

        windows.releaseAll()
                .forEach(window -> closeWindow.close(window.viewer(), window.owner(), window.vault(), a.items));

        assertThat(handedBack).containsExactly("Alex:42");
        assertThat(vaults.find(VaultId.of(ALEX, 1))).isEmpty();
        assertThat(windows.count()).isZero();
    }

    @Test
    void aStaffWindowKeepsItsItemsInTheVaultEvenAfterADelete() {
        Vault bought = openVault.open(ALEX, 1).orElseThrow();
        Chest staff = show(SAM, bought);
        staff.items = X;
        deleteVault.delete(ALEX, 1);

        close(bought, staff);

        assertThat(handedBack).isEmpty();
        Vault kept = vaults.find(VaultId.of(ALEX, 1)).orElseThrow();
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

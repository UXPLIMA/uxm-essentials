package com.uxplima.uxmessentials.vaults.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.junit.jupiter.api.Test;

/**
 * Deleting your own vault refunds the configured amount once, never more than the vault cost and in the currency
 * it was paid in; a free vault, a staff delete or a missing vault pays nothing.
 */
class DeleteVaultTest {

    private static final VaultChargeSettings CREATE_5_REFUND_5 = costs("5", "0", "5");

    private final InMemoryVaults vaults = new InMemoryVaults();
    private final InterleavedVaults store = new InterleavedVaults(vaults);
    private final FakePermissions permissions = new FakePermissions();
    private final VaultPurse purse = new VaultPurse("100");
    private final List<String> lines = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();

    private static VaultChargeSettings costs(String create, String open, String refund) {
        return new VaultChargeSettings(new BigDecimal(create), new BigDecimal(open), new BigDecimal(refund));
    }

    private VaultCharge charge(VaultChargeSettings costs) {
        return new VaultCharge(permissions, Optional.of(purse), costs);
    }

    private VaultNotifier notifier() {
        return new VaultNotifier(
                (viewer, key, placeholders) -> {
                    lines.add(viewer.name() + ":" + key.key());
                    return key.key();
                },
                (viewer, text) -> {});
    }

    private DeleteVault deleteVault(VaultChargeSettings costs) {
        return new DeleteVault(store, charge(costs), new Audit(), notifier());
    }

    private SaveVault saveVault() {
        return new SaveVault(store, new RecordingEvents());
    }

    private DeleteVault deleteVault() {
        return deleteVault(CREATE_5_REFUND_5);
    }

    /** Buy vault {@code index} the way a player does, through /vault, paying whatever {@code costs} charges. */
    private Vault buy(int index, VaultChargeSettings costs) {
        OpenVault open = new OpenVault(
                store, new VaultAmountQuota(permissions, 10), new VaultSizeQuota(permissions, 1), charge(costs), CLOCK);
        return open.open(ALEX, index).orElseThrow().vault();
    }

    @Test
    void anOwnerDeletingTheirVaultIsRefundedOnce() {
        buy(1, CREATE_5_REFUND_5);
        DeleteVault delete = deleteVault();

        assertThat(delete.delete(ALEX, 1).isOk()).isTrue();
        assertThat(delete.delete(ALEX, 1).errorOrThrow()).isEqualTo(VaultError.DELETE_UNKNOWN);

        assertThat(purse.moves()).containsExactly("-5", "+5 coins");
        assertThat(vaults.find(VaultId.of(ALEX, 1))).isEmpty();
    }

    @Test
    void aRefundSetAboveTheCreateCostPaysBackOnlyWhatTheVaultCost() {
        VaultChargeSettings costs = costs("3", "0", "10");
        buy(1, costs);

        deleteVault(costs).delete(ALEX, 1);

        assertThat(purse.moves()).containsExactly("-3", "+3 coins");
        assertThat(purse.balance()).isEqualByComparingTo("100");
    }

    @Test
    void aCostRaisedAfterTheVaultWasBoughtRefundsOnlyWhatWasPaid() {
        buy(1, CREATE_5_REFUND_5);

        deleteVault(costs("50", "0", "50")).delete(ALEX, 1);

        assertThat(purse.moves()).containsExactly("-5", "+5 coins");
    }

    @Test
    void aRefundBelowWhatWasPaidIsPaidInFull() {
        buy(1, costs("10", "0", "4"));

        deleteVault(costs("10", "0", "4")).delete(ALEX, 1);

        assertThat(purse.moves()).containsExactly("-10", "+4 coins");
    }

    @Test
    void aVaultThatCostNothingRefundsNothing() {
        buy(1, costs("0", "0", "5"));

        assertThat(deleteVault().delete(ALEX, 1).isOk()).isTrue();

        assertThat(purse.moves()).isEmpty();
        assertThat(purse.balance()).isEqualByComparingTo("100");
    }

    @Test
    void theOpenFeeIsNotPartOfWhatTheVaultCost() {
        VaultChargeSettings costs = costs("0", "2", "5");
        buy(1, costs);

        deleteVault(costs).delete(ALEX, 1);

        assertThat(purse.moves()).containsExactly("-2");
    }

    @Test
    void aVaultTheBypassHolderGotForFreeRefundsNothingEvenAfterTheNodeIsGone() {
        permissions.grant(ALEX, DestinationPermissions.VAULT_FREE);
        buy(1, CREATE_5_REFUND_5);
        FakePermissions none = new FakePermissions();
        DeleteVault delete = new DeleteVault(
                vaults,
                new VaultCharge(none, Optional.of(purse), CREATE_5_REFUND_5),
                new Audit(),
                new VaultNotifier((viewer, key, placeholders) -> key.key(), (viewer, text) -> {}));

        delete.delete(ALEX, 1);

        assertThat(purse.moves()).isEmpty();
    }

    @Test
    void aVaultFromBeforePaymentsWereRecordedRefundsNothing() {
        vaults.put(Vault.allocate(VaultId.of(ALEX, 1), new VaultSize(1), NOW));

        assertThat(deleteVault().delete(ALEX, 1).isOk()).isTrue();

        assertThat(purse.moves()).isEmpty();
    }

    @Test
    void openingAndDeletingInALoopNeverRaisesTheBalance() {
        VaultChargeSettings costs = costs("3", "1", "10");
        DeleteVault delete = deleteVault(costs);

        for (int round = 0; round < 20; round++) {
            buy(1, costs);
            assertThat(delete.delete(ALEX, 1).isOk()).isTrue();
            assertThat(purse.balance()).isLessThanOrEqualTo(new BigDecimal("100"));
        }

        assertThat(purse.balance()).isEqualByComparingTo("80");
    }

    @Test
    void theRefundGoesBackInTheCurrencyTheVaultWasPaidIn() {
        buy(1, CREATE_5_REFUND_5);
        purse.chargeIn("gems");

        deleteVault().delete(ALEX, 1);

        assertThat(purse.moves()).containsExactly("-5", "+5 coins");
        assertThat(purse.balance("coins")).isEqualByComparingTo("100");
        assertThat(purse.balance("gems")).isZero();
    }

    @Test
    void theOwnersWindowClosedAfterTheVaultWasDeletedDoesNotBringItBack() {
        Vault opened = buy(1, CREATE_5_REFUND_5);
        deleteVault().delete(ALEX, 1);

        boolean stored = saveVault()
                .save(ALEX, opened, VaultContents.of(new byte[] {1}), NOW, "close", null)
                .isPresent();

        assertThat(stored).isFalse();
        assertThat(vaults.find(VaultId.of(ALEX, 1))).isEmpty();
        assertThat(purse.moves()).containsExactly("-5", "+5 coins");
    }

    @Test
    void aVaultAStaffWindowKeptAfterTheDeleteIsNeverRefundedAgain() {
        Vault opened = buy(1, CREATE_5_REFUND_5);
        deleteVault().delete(ALEX, 1);

        saveVault().keep(ALEX, opened, VaultContents.of(new byte[] {1}), NOW, "staff");
        assertThat(deleteVault().delete(ALEX, 1).isOk()).isTrue();

        assertThat(purse.moves()).containsExactly("-5", "+5 coins");
    }

    @Test
    void aDeleteThatLostTheRaceRefundsNothingEvenWhenTheRowWasBroughtBack() {
        Vault opened = buy(1, CREATE_5_REFUND_5);
        DeleteVault first = deleteVault();
        // Between this delete's read and its delete, a second delete wins and a staff window that was still open
        // puts the row back, without a payment.
        store.beforeNextDelete(() -> {
            assertThat(deleteVault().delete(ALEX, 1).isOk()).isTrue();
            saveVault().keep(ALEX, opened, VaultContents.of(new byte[] {2}), NOW, "staff");
        });

        assertThat(first.delete(ALEX, 1).errorOrThrow()).isEqualTo(VaultError.DELETE_UNKNOWN);

        assertThat(purse.moves()).containsExactly("-5", "+5 coins");
        assertThat(vaults.find(VaultId.of(ALEX, 1))).isPresent();
    }

    @Test
    void aStaffDeleteThatLostTheRaceRemovesNothing() {
        Vault opened = buy(2, CREATE_5_REFUND_5);
        store.beforeNextDelete(() -> {
            deleteVault().delete(ALEX, 2);
            saveVault().keep(ALEX, opened, VaultContents.of(new byte[] {2}), NOW, "staff");
        });

        assertThat(deleteVault().deleteOther(SAM, ALEX, 2).errorOrThrow()).isEqualTo(VaultError.DELETE_UNKNOWN);

        assertThat(audit).isEmpty();
        assertThat(vaults.find(VaultId.of(ALEX, 2))).isPresent();
    }

    @Test
    void renamingOrSettingTheIconOfAVaultDeletedMeanwhileDoesNotBringItBack() {
        buy(1, CREATE_5_REFUND_5);
        store.beforeNextUpdate(() -> deleteVault().delete(ALEX, 1));
        assertThat(new RenameVault(store, notifier()).rename(ALEX, 1, "loot").errorOrThrow())
                .isEqualTo(VaultError.VAULT_UNKNOWN);
        assertThat(vaults.find(VaultId.of(ALEX, 1))).isEmpty();

        buy(1, CREATE_5_REFUND_5);
        store.beforeNextUpdate(() -> deleteVault().delete(ALEX, 1));
        assertThat(new SetVaultIcon(store, notifier())
                        .setIcon(ALEX, 1, "DIAMOND")
                        .errorOrThrow())
                .isEqualTo(VaultError.VAULT_UNKNOWN);
        assertThat(vaults.find(VaultId.of(ALEX, 1))).isEmpty();

        assertThat(purse.moves()).containsExactly("-5", "+5 coins", "-5", "+5 coins");
    }

    @Test
    void deletingAnIndexWithNoVaultPaysNothing() {
        assertThat(deleteVault().delete(ALEX, 3).errorOrThrow()).isEqualTo(VaultError.DELETE_UNKNOWN);

        assertThat(purse.moves()).isEmpty();
    }

    @Test
    void aRefusedRefundStillDeletesTheVault() {
        buy(1, CREATE_5_REFUND_5);
        purse.refuseDeposits();

        assertThat(deleteVault().delete(ALEX, 1).isOk()).isTrue();

        assertThat(vaults.find(VaultId.of(ALEX, 1))).isEmpty();
        assertThat(lines).containsExactly("Alex:vaults.deleted");
    }

    @Test
    void aStaffDeleteIsAuditedAndRefundsNobody() {
        buy(2, CREATE_5_REFUND_5);

        assertThat(deleteVault().deleteOther(SAM, ALEX, 2).isOk()).isTrue();

        assertThat(purse.moves()).containsExactly("-5");
        assertThat(audit).containsExactly("Sam deleted Alex#2");
        assertThat(vaults.find(VaultId.of(ALEX, 2))).isEmpty();
    }

    @Test
    void aStaffDeleteOfAMissingVaultIsNotAudited() {
        assertThat(deleteVault().deleteOther(SAM, ALEX, 2).errorOrThrow()).isEqualTo(VaultError.DELETE_UNKNOWN);

        assertThat(audit).isEmpty();
    }

    /** Keeps the admin delete lines; the other audit events are not what these tests look at. */
    private final class Audit implements VaultAudit {

        @Override
        public void adminOpened(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {}

        @Override
        public void adminDeleted(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {
            audit.add(actor.name() + " deleted " + owner.name() + "#" + index);
        }

        @Override
        public void purged(int count) {}
    }
}

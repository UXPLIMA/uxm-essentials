package com.uxplima.uxmessentials.vaults.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.junit.jupiter.api.Test;

/** Deleting your own vault pays the refund exactly once; a staff delete or a missing vault pays nothing. */
class DeleteVaultTest {

    private static final VaultChargeSettings REFUND_5 =
            new VaultChargeSettings(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5"));

    private final InMemoryVaults vaults = new InMemoryVaults();
    private final FakePermissions permissions = new FakePermissions();
    private final VaultPurse purse = new VaultPurse("0");
    private final List<String> lines = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();

    private DeleteVault deleteVault() {
        VaultNotifier notifier = new VaultNotifier(
                (viewer, key, placeholders) -> {
                    lines.add(viewer.name() + ":" + key.key());
                    return key.key();
                },
                (viewer, text) -> {});
        return new DeleteVault(
                vaults, new VaultCharge(permissions, Optional.of(purse), REFUND_5), new Audit(), notifier);
    }

    private void own(PlayerRef owner, int index) {
        vaults.put(Vault.allocate(VaultId.of(owner, index), new VaultSize(1), NOW));
    }

    @Test
    void anOwnerDeletingTheirVaultIsRefundedOnce() {
        own(ALEX, 1);
        DeleteVault delete = deleteVault();

        assertThat(delete.delete(ALEX, 1).isOk()).isTrue();
        assertThat(delete.delete(ALEX, 1).errorOrThrow()).isEqualTo(VaultError.DELETE_UNKNOWN);

        assertThat(purse.moves()).containsExactly("+5");
        assertThat(vaults.find(VaultId.of(ALEX, 1))).isEmpty();
    }

    @Test
    void deletingAnIndexWithNoVaultPaysNothing() {
        assertThat(deleteVault().delete(ALEX, 3).errorOrThrow()).isEqualTo(VaultError.DELETE_UNKNOWN);

        assertThat(purse.moves()).isEmpty();
    }

    @Test
    void aRefusedRefundStillDeletesTheVault() {
        own(ALEX, 1);
        purse.refuseDeposits();

        assertThat(deleteVault().delete(ALEX, 1).isOk()).isTrue();

        assertThat(vaults.find(VaultId.of(ALEX, 1))).isEmpty();
        assertThat(lines).containsExactly("Alex:vaults.deleted");
    }

    @Test
    void theBypassHolderWasNeverChargedSoGetsNoRefund() {
        own(ALEX, 1);
        permissions.grant(ALEX, "uxmessentials.vault.free");

        deleteVault().delete(ALEX, 1);

        assertThat(purse.moves()).isEmpty();
    }

    @Test
    void aStaffDeleteIsAuditedAndRefundsNobody() {
        own(ALEX, 2);

        assertThat(deleteVault().deleteOther(SAM, ALEX, 2).isOk()).isTrue();

        assertThat(purse.moves()).isEmpty();
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

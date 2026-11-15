package com.uxplima.uxmessentials.vaults.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.junit.jupiter.api.Test;

/** Opening a vault checks the count quota by permission, charges once per open and never charges for a refusal. */
class OpenVaultTest {

    private static final VaultChargeSettings CREATE_10_OPEN_2 =
            new VaultChargeSettings(new BigDecimal("10"), new BigDecimal("2"), BigDecimal.ZERO);

    private final InMemoryVaults vaults = new InMemoryVaults();
    private final FakePermissions permissions = new FakePermissions();
    private final VaultPurse purse = new VaultPurse("100");

    private OpenVault openVault(VaultChargeSettings costs, Optional<VaultEconomy> economy) {
        return new OpenVault(
                vaults,
                new VaultAmountQuota(permissions, 1),
                new VaultSizeQuota(permissions, 3),
                new VaultCharge(permissions, economy, costs),
                CLOCK);
    }

    private OpenVault openVault(VaultChargeSettings costs) {
        return openVault(costs, Optional.of(purse));
    }

    @Test
    void aFirstOpenPaysTheCreateAndOpenFeesInOneWithdrawal() {
        Result<Vault, VaultError> opened = openVault(CREATE_10_OPEN_2).open(ALEX, 1);

        assertThat(opened.isOk()).isTrue();
        assertThat(purse.moves()).containsExactly("-12");
        assertThat(vaults.find(VaultId.of(ALEX, 1))).isPresent();
    }

    @Test
    void aReopenPaysOnlyTheOpenFee() {
        OpenVault open = openVault(CREATE_10_OPEN_2);
        open.open(ALEX, 1);

        open.open(ALEX, 1);

        assertThat(purse.moves()).containsExactly("-12", "-2");
        assertThat(purse.balance()).isEqualByComparingTo("86");
    }

    @Test
    void aPlayerWhoCannotAffordANewVaultIsRefusedAndNoRowIsWritten() {
        VaultPurse poor = new VaultPurse("11");

        Result<Vault, VaultError> opened =
                openVault(CREATE_10_OPEN_2, Optional.of(poor)).open(ALEX, 1);

        assertThat(opened.errorOrThrow()).isEqualTo(VaultError.CANNOT_AFFORD);
        assertThat(poor.balance()).isEqualByComparingTo("11");
        assertThat(vaults.size()).isZero();
    }

    @Test
    void aReopenThePlayerCannotAffordKeepsTheStoredItemsUntouched() {
        Vault stored = Vault.allocate(VaultId.of(ALEX, 1), new VaultSize(3), NOW)
                .withContents(ALEX, VaultContents.of(new byte[] {1, 2, 3}), NOW)
                .result();
        vaults.put(stored);
        VaultPurse poor = new VaultPurse("1");

        Result<Vault, VaultError> opened =
                openVault(CREATE_10_OPEN_2, Optional.of(poor)).open(ALEX, 1);

        assertThat(opened.errorOrThrow()).isEqualTo(VaultError.CANNOT_AFFORD);
        assertThat(poor.moves()).isEmpty();
        assertThat(vaults.find(VaultId.of(ALEX, 1))).contains(stored);
    }

    @Test
    void anIndexBeyondTheCountQuotaIsRefusedBeforeAnyCharge() {
        Result<Vault, VaultError> opened = openVault(CREATE_10_OPEN_2).open(ALEX, 2);

        assertThat(opened.errorOrThrow()).isEqualTo(VaultError.AMOUNT_EXCEEDED);
        assertThat(purse.moves()).isEmpty();
        assertThat(vaults.size()).isZero();
    }

    @Test
    void aHigherAmountNodeOpensMoreVaults() {
        permissions.quota(VaultAmountQuota.FAMILY.node(), QuotaResult.limited(3));

        assertThat(openVault(VaultChargeSettings.allFree()).open(ALEX, 3).isOk())
                .isTrue();
        assertThat(openVault(VaultChargeSettings.allFree()).open(ALEX, 4).errorOrThrow())
                .isEqualTo(VaultError.AMOUNT_EXCEEDED);
    }

    @Test
    void theUnlimitedSentinelOpensAnyIndex() {
        permissions.quota(VaultAmountQuota.FAMILY.node(), QuotaResult.unlimited());

        assertThat(openVault(VaultChargeSettings.allFree()).open(ALEX, 500).isOk())
                .isTrue();
    }

    @Test
    void aVaultAlreadyOwnedStillOpensAfterTheQuotaShrank() {
        vaults.put(Vault.allocate(VaultId.of(ALEX, 4), new VaultSize(3), NOW));
        permissions.quota(VaultAmountQuota.FAMILY.node(), QuotaResult.limited(0));

        assertThat(openVault(VaultChargeSettings.allFree()).open(ALEX, 4).isOk())
                .isTrue();
    }

    @Test
    void theBypassNodeOpensANewVaultForFree() {
        permissions.grant(ALEX, "uxmessentials.vault.free");

        assertThat(openVault(CREATE_10_OPEN_2).open(ALEX, 1).isOk()).isTrue();

        assertThat(purse.moves()).isEmpty();
    }

    @Test
    void withoutAnEconomyAConfiguredCostIsIgnored() {
        assertThat(openVault(CREATE_10_OPEN_2, Optional.empty()).open(ALEX, 1).isOk())
                .isTrue();
    }

    @Test
    void aChangedSizeNodeResizesTheVaultOnOpenAndKeepsItsItems() {
        VaultContents items = VaultContents.of(new byte[] {7});
        vaults.put(Vault.allocate(VaultId.of(ALEX, 1), new VaultSize(3), NOW)
                .withContents(ALEX, items, NOW)
                .result());
        permissions.quota(VaultSizeQuota.FAMILY.node(), QuotaResult.limited(6));

        Vault opened = openVault(VaultChargeSettings.allFree()).open(ALEX, 1).orElseThrow();

        assertThat(opened.size()).isEqualTo(new VaultSize(6));
        assertThat(vaults.find(VaultId.of(ALEX, 1)).orElseThrow().size()).isEqualTo(new VaultSize(6));
        assertThat(vaults.find(VaultId.of(ALEX, 1)).orElseThrow().contents()).isEqualTo(items);
    }

    @Test
    void anUnchangedSizeDoesNotRewriteTheRowOnOpen() {
        vaults.put(Vault.allocate(VaultId.of(ALEX, 1), new VaultSize(3), NOW));

        openVault(VaultChargeSettings.allFree()).open(ALEX, 1);

        assertThat(vaults.saves()).isZero();
    }

    @Test
    void aSizeNodeOutsideTheChestRangeIsClampedToWhatAGuiCanShow() {
        permissions.quota(VaultSizeQuota.FAMILY.node(), QuotaResult.limited(40));
        assertThat(new VaultSizeQuota(permissions, 3).resolve(ALEX)).isEqualTo(new VaultSize(6));

        permissions.quota(VaultSizeQuota.FAMILY.node(), QuotaResult.limited(0));
        assertThat(new VaultSizeQuota(permissions, 3).resolve(ALEX)).isEqualTo(new VaultSize(1));

        permissions.quota(VaultSizeQuota.FAMILY.node(), QuotaResult.unlimited());
        assertThat(new VaultSizeQuota(permissions, 3).resolve(ALEX)).isEqualTo(new VaultSize(6));
    }

    @Test
    void savingAClosedVaultStoresExactlyTheNewItemsOnce() {
        RecordingEvents events = new RecordingEvents();
        Vault opened = openVault(VaultChargeSettings.allFree()).open(ALEX, 1).orElseThrow();
        int savesAfterOpen = vaults.saves();
        Clock later = Clock.offset(CLOCK, Duration.ofMinutes(5));
        VaultContents items = VaultContents.of(new byte[] {4, 5});

        new SaveVault(vaults, events, later).save(ALEX, opened, items);

        Vault stored = vaults.find(VaultId.of(ALEX, 1)).orElseThrow();
        assertThat(stored.contents()).isEqualTo(items);
        assertThat(stored.lastTouched()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(vaults.saves()).isEqualTo(savesAfterOpen + 1);
        assertThat(vaults.size()).isEqualTo(1);
        assertThat(events.published()).hasSize(1);
    }

    @Test
    void thePurgeRemovesOnlyVaultsUntouchedForTheWholeWindow() {
        vaults.put(Vault.allocate(VaultId.of(ALEX, 1), new VaultSize(1), NOW.minus(Duration.ofDays(31))));
        vaults.put(Vault.allocate(VaultId.of(ALEX, 2), new VaultSize(1), NOW.minus(Duration.ofDays(30))));
        vaults.put(Vault.allocate(VaultId.of(ALEX, 3), new VaultSize(1), NOW.minus(Duration.ofDays(1))));

        int removed = new PurgeInactiveVaults(vaults).purge(Duration.ofDays(30), NOW);

        assertThat(removed).isEqualTo(1);
        assertThat(vaults.ownedIndices(ALEX)).containsExactly(2, 3);
    }
}

package com.uxplima.uxmessentials.vaults.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.junit.jupiter.api.Test;

/**
 * Staff opening another player's vault see what it holds at the height it was saved, whether or not the owner's size
 * quota can be read here, and every open is audited.
 */
class OpenAdminVaultTest {

    private static final int CONFIG_ROWS = 3;

    private final InMemoryVaults vaults = new InMemoryVaults();
    private final FakePermissions permissions = new FakePermissions();
    private final List<String> audit = new ArrayList<>();

    private final OpenAdminVault open =
            new OpenAdminVault(vaults, new VaultSizeQuota(permissions, CONFIG_ROWS), new Audit(), CLOCK);

    @Test
    void aVaultSavedTallerThanTheQuotaReadsHereOpensAtItsSavedHeight() {
        VaultContents items = VaultContents.of(new byte[] {4, 2});
        vaults.put(Vault.allocate(VaultId.of(ALEX, 1), new VaultSize(6), NOW)
                .withContents(ALEX, items, NOW)
                .result());

        Vault opened = open.open(SAM, ALEX, 1);

        assertThat(opened.size()).isEqualTo(new VaultSize(6));
        assertThat(opened.contents()).isEqualTo(items);
        assertThat(audit).containsExactly("Sam opened Alex#1");
    }

    @Test
    void aVaultSavedShorterThanTheOwnersQuotaOpensAtTheQuota() {
        permissions.holdTier(ALEX, DestinationPermissions.VAULT_SIZE, 5);
        vaults.put(Vault.allocate(VaultId.of(ALEX, 1), new VaultSize(2), NOW));

        Vault opened = open.open(SAM, ALEX, 1);

        assertThat(opened.size()).isEqualTo(new VaultSize(5));
    }

    @Test
    void aVaultTheOwnerHasNotUsedOpensEmptyAtTheirQuota() {
        Vault opened = open.open(SAM, ALEX, 2);

        assertThat(opened.size()).isEqualTo(new VaultSize(CONFIG_ROWS));
        assertThat(opened.contents().isEmpty()).isTrue();
        assertThat(audit).containsExactly("Sam opened Alex#2");
    }

    private final class Audit implements VaultAudit {

        @Override
        public void adminOpened(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {
            audit.add(actor.name() + " opened " + owner.name() + "#" + index);
        }

        @Override
        public void adminDeleted(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {}

        @Override
        public void purged(int count) {}
    }
}

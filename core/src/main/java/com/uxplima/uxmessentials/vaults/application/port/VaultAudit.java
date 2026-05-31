package com.uxplima.uxmessentials.vaults.application.port;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the vaults audit trail — the {@code com.uxplima.uxmessentials.audit} channel
 * (docs/09-deployment.md §Audit logging), mirroring the moderation and itemworld audit. The admin form
 * ({@code /vault <player> [n]}) lets staff open and inspect another player's vault, so every admin open emits
 * exactly one structured {@code event=vault_admin_open actor=<staff> owner=<player> idx=<n>} line an operator
 * can route to a retained file. A player opening their own vault is not audited — only the staff override is.
 * The implementation lands with the bukkit-adapter; the use cases depend only on this contract.
 */
public interface VaultAudit {

    /**
     * {@code event=vault_admin_open} — a {@code /vault <player> [n]} staff open of another player's vault.
     *
     * @param actor the staff member performing the override
     * @param owner the audited player whose vault was opened
     * @param ownerUuid the audited player's uuid (logged for stable identity even after a rename)
     * @param index the one-based vault number opened
     */
    void adminOpened(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index);
}

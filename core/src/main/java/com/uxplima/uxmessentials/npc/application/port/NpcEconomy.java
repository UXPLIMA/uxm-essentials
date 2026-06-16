package com.uxplima.uxmessentials.npc.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow economy seam the npc context owns so a {@code COST} click action can charge the clicking viewer
 * <em>without</em> a hard dependency on the economy context. This is the entire economy surface the npc action
 * runner needs — a single guarded withdrawal — expressed in npc's own terms; the economy context supplies an
 * adapter that bridges this to its {@code EconomyProvider}/{@code Wallet}, and the npc context never imports an
 * economy type (mirrors the kits {@code KitEconomy} and warps {@code WarpEconomy} seams).
 *
 * <p>Soft coupling: this port is injected as an {@link java.util.Optional} into the action runner. When no
 * provider is present, a {@code COST} action is skipped (the gate is ignored and the chain continues), so a
 * server without economy still runs the rest of an NPC's actions. When a provider is present, the runner charges
 * once through {@link #withdraw} and aborts the remaining actions only when the debit reports the funds were
 * insufficient.
 */
public interface NpcEconomy {

    /**
     * Withdraw {@code amount} of {@code currencyId} from {@code who}'s balance, returning {@code true} on a
     * successful debit and {@code false} when the balance was insufficient. The debit is guarded at the source so
     * a {@code true} return means the money left the account exactly once — there is no separate balance read to
     * race against, so a {@code COST} action can never double-charge.
     */
    boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId);
}

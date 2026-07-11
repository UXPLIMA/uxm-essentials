package com.uxplima.uxmessentials.playerwarps.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The narrow economy seam the player-warps context owns so a per-warp entry price can be charged <em>without</em>
 * a hard dependency on the economy context. It mirrors the sibling {@code warps} {@code WarpEconomy} — expressed
 * purely in this context's own terms ({@link BigDecimal} + a {@code currencyId} string, never an economy type) —
 * but is richer because a player warp has an owner who banks the takings: a use of a priced warp both debits the
 * visitor and accrues to the owner's bank in one step.
 *
 * <p>Soft coupling: the port is injected into {@code UsePlayerWarp} as an {@link java.util.Optional}. With no
 * provider present a priced warp's cost is simply ignored at use time — the warp still teleports, for free — so
 * the context stays usable before economy is wired (the {@code WarpEconomy} precedent). With a provider present
 * the charge is the gate's guarded last step: it runs only after every other gate has passed, and the visit and
 * teleport happen only once it succeeds.
 *
 * <p>The configured owner <em>cut</em> — the share the server keeps rather than passing to the owner — is the
 * implementation's concern (P4-T4 reads it from config), not a gate parameter: the gate passes only the
 * {@code price} and its {@code currencyId}, keeping this seam as narrow as {@code WarpEconomy}. The
 * {@code chargeAndAccrue} contract is deliberately charge-then-settle with no check-then-charge window, so two
 * simultaneous uses can never both pass an affordability probe and then overdraw.
 */
public interface PlayerWarpEconomy {

    /**
     * Guarded debit of {@code payer} by {@code price} in {@code currencyId} and accrual of the price minus the
     * configured cut to the warp owner's bank, as one transaction. Under the native provider this is a single
     * atomic step; under a foreign provider a debit that succeeds but whose accrual then fails is unwound with a
     * compensating credit to the payer and reported as {@link ChargeError#ACCRUAL_FAILED}. There is no
     * check-then-charge: the debit either takes in full or reports {@link ChargeError#INSUFFICIENT_FUNDS}.
     */
    Result<Unit, ChargeError> chargeAndAccrue(PlayerRef payer, PlayerWarpId warp, BigDecimal price, String currencyId);

    /**
     * True when {@code who} could currently afford {@code amount} of {@code currencyId}. For UI preview only —
     * the gate never calls this, because an affordability probe followed by a separate charge is exactly the
     * double-spend window {@link #chargeAndAccrue} closes.
     */
    boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId);

    /** Pay out the warp's accrued earnings to {@code to} (the owner), used by {@code /pwarp withdraw} (P4-T6). */
    Result<Unit, ChargeError> withdraw(PlayerWarpId warp, PlayerRef to);

    /** Credit {@code amount} of {@code currencyId} back to {@code to}, used by the refund paths in P7. */
    Result<Unit, ChargeError> refund(PlayerRef to, BigDecimal amount, String currencyId);
}

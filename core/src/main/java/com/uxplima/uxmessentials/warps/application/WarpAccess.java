package com.uxplima.uxmessentials.warps.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpError;

/**
 * The access gate a {@code /warp} passes before the hop is delegated: the per-warp permission node, the
 * warp's optional extra permission, and, only when an economy provider is present, the per-warp cost.
 * Extracted from {@code UseWarp} so the use case stays a thin orchestrator. The permission check and the
 * charge are two steps because every other check of a use (lock, password, safety, and for a warp on another
 * backend the teleport rules) runs between them, so nothing refused is ever charged.
 *
 * <p>This is where the economy <em>soft coupling</em> is expressed. The economy provider is an
 * {@link Optional} injected at wiring time: when it is absent, a warp's recorded cost is ignored and the
 * warp is usable for free; when it is present, the cost is charged through the narrow {@link WarpEconomy}
 * seam after the permission gates pass. The warps context therefore never hard-depends on the economy
 * context: economy landing in P3 only flips this {@code Optional} from empty to present.
 */
public final class WarpAccess {

    private final Permissions permissions;
    private final Optional<WarpEconomy> economy;

    public WarpAccess(Permissions permissions, Optional<WarpEconomy> economy) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    /**
     * Whether {@code who} holds every permission {@code warp} asks for: the per-warp node and its optional extra
     * one. Asked before anything else, and before {@link #charge}, so a refused use costs nothing.
     */
    public boolean permits(PlayerRef who, Warp warp) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        if (!permissions.has(who, warp.name().useNode())) {
            return false;
        }
        return warp.requiredPermission().map(node -> permissions.has(who, node)).orElse(true);
    }

    /**
     * Charge {@code warp}'s cost to {@code who} when it has one and a provider is present. Returns exactly what was
     * debited (empty when nothing was), so a use that never moves the player can give that back, or
     * {@link WarpError#CANNOT_AFFORD} when the debit failed. Called last, once every other check has passed.
     */
    public Result<Optional<WarpCost>, WarpError> charge(PlayerRef who, Warp warp) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        if (!warp.hasCost() || economy.isEmpty()) {
            // No charge to make, or no economy provider wired. The cost is recorded but ignored at use
            // time so warps never hard-depends on the economy context.
            return Result.ok(Optional.empty());
        }
        WarpCost cost = warp.cost();
        if (!economy.get().withdraw(who, cost.amount(), cost.currencyId())) {
            return Result.err(WarpError.CANNOT_AFFORD);
        }
        return Result.ok(Optional.of(cost));
    }

    /**
     * Give back {@code cost}, which {@link #charge} took from {@code who} for a use that never moved them, through the
     * same provider and in the same currency. True once the credit has landed.
     */
    public boolean refund(PlayerRef who, WarpCost cost) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(cost, "cost");
        return economy.isPresent() && economy.get().refund(who, cost.amount(), cost.currencyId());
    }
}

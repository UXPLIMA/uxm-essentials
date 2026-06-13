package com.uxplima.uxmessentials.homes.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.homes.domain.HomeCost;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The economy charge gate for home actions: resolves the cost for a {@link HomeChargeKind}, skips the
 * charge when it is free, when no economy provider is wired, or when the player holds the bypass node,
 * and otherwise delegates the withdrawal to {@link HomeEconomy}.
 *
 * <p>This is where the economy <em>soft coupling</em> is expressed. The provider is an {@link Optional}
 * injected at wiring time: when it is absent a configured cost is ignored and the action proceeds for
 * free; when it is present the cost is charged through the narrow {@link HomeEconomy} seam after all
 * other guards have passed. The homes context therefore never hard-depends on the economy context.
 */
public final class HomeCharge {

    private static final String BYPASS_NODE = "uxmessentials.home.bypass.cost";

    private final Permissions permissions;
    private final Optional<HomeEconomy> economy;
    private final HomeChargeSettings settings;

    public HomeCharge(Permissions permissions, Optional<HomeEconomy> economy, HomeChargeSettings settings) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Attempt to charge {@code who} for {@code kind}. Returns success immediately when the action is
     * free, when no economy provider is present, or when the player has the bypass permission. Returns
     * {@link HomeError#CANNOT_AFFORD} when the player's balance was insufficient.
     */
    public Result<Unit, HomeError> charge(PlayerRef who, HomeChargeKind kind) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        HomeCost cost = settings.forKind(kind);
        if (cost.isFree() || economy.isEmpty() || permissions.has(who, BYPASS_NODE)) {
            return Result.ok();
        }
        boolean withdrawn = economy.get().withdraw(who, cost.amount(), cost.currencyId());
        return withdrawn ? Result.ok() : Result.err(HomeError.CANNOT_AFFORD);
    }
}

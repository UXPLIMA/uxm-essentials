package com.uxplima.uxmessentials.kits.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownStartPhase;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The claim gate a {@code /kit} passes before the items are granted, applying the claim rules in one place
 * so {@link ClaimKit} stays a thin orchestrator. The order is deliberate: the per-kit permission first
 * (cheapest, and the most informative refusal), then the one-time stamp (a consumed one-time kit is a
 * permanent no), then the cooldown (a repeatable kit's rate limit), and only last the charge — so a kit the
 * player cannot afford is never charged and an over-cooldown kit never burns their money.
 *
 * <p>This is where the economy <em>soft coupling</em> lives. The economy provider is an {@link Optional}
 * injected at wiring time: when it is absent, a kit's recorded cost is ignored and the kit is claimable for
 * free; when it is present, the cost is charged through the narrow {@link KitEconomy} seam after every other
 * gate passes. The kits context therefore never hard-depends on the economy context.
 *
 * <p>The cooldown resolves through the shared {@link Cooldowns} port against the {@code kit} tier node
 * ({@code uxmessentials.kit.cooldown.<seconds>}, lowest wins) while keying its stamp per kit id, so each kit
 * rate-limits independently. The {@code uxmessentials.kit.cooldown.bypass} node both skips the cooldown and,
 * by convention, lets a holder re-claim a one-time kit — that bypass is checked here for the one-time gate.
 */
public final class KitAccess {

    private static final String COOLDOWN_BYPASS_NODE = "uxmessentials.kit.cooldown.bypass";

    private final Permissions permissions;
    private final Cooldowns cooldowns;
    private final KitClaimStore claims;
    private final Optional<KitEconomy> economy;

    public KitAccess(Permissions permissions, Cooldowns cooldowns, KitClaimStore claims, Optional<KitEconomy> economy) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    /**
     * Check whether {@code who} may claim {@code kit} and, when a cost applies and a provider is present,
     * charge it. Returns the first failing gate, or success once the player has been admitted (and charged,
     * if applicable). Side effects beyond the charge — stamping the cooldown and the one-time mark — are the
     * caller's job, run only after the grant succeeds.
     */
    public Result<Unit, KitError> admit(PlayerRef who, KitDefinition kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        if (kit.requiresPermission() && !permissions.has(who, kit.id().permissionNode())) {
            return Result.err(KitError.NO_PERMISSION);
        }
        if (kit.isOneTime() && claims.hasClaimed(who, kit.id()) && !permissions.has(who, COOLDOWN_BYPASS_NODE)) {
            return Result.err(KitError.ALREADY_CLAIMED);
        }
        if (cooldowns.check(who, cooldownKind(who, kit)).isErr()) {
            return Result.err(KitError.ON_COOLDOWN);
        }
        return charge(who, kit);
    }

    /** Start the cooldown clock and record the one-time stamp after a successful grant. */
    public void recordClaim(PlayerRef who, KitDefinition kit) {
        cooldowns.stamp(who, cooldownKind(who, kit));
        if (kit.isOneTime()) {
            claims.markClaimed(who, kit.id());
        }
    }

    /** The remaining cooldown for {@code who} on {@code kit}; ok when ready. */
    public Result<Unit, java.time.Duration> remaining(PlayerRef who, KitDefinition kit) {
        return cooldowns.check(who, cooldownKind(who, kit));
    }

    private Result<Unit, KitError> charge(PlayerRef who, KitDefinition kit) {
        if (!kit.hasCost() || economy.isEmpty()) {
            return Result.ok();
        }
        KitEconomy provider = economy.get();
        if (!provider.withdraw(who, kit.cost().amount(), kit.cost().currencyId())) {
            return Result.err(KitError.CANNOT_AFFORD);
        }
        return Result.ok();
    }

    private CooldownKind cooldownKind(PlayerRef who, KitDefinition kit) {
        long seconds = resolveCooldownSeconds(who, kit);
        return CooldownKind.scoped("kit", "kit." + kit.id().value(), seconds, CooldownStartPhase.TELEPORT);
    }

    public boolean hasPermission(PlayerRef who, KitDefinition kit) {
        return !kit.requiresPermission() || permissions.has(who, kit.id().permissionNode());
    }

    public boolean hasClaimedOneTime(PlayerRef who, KitDefinition kit) {
        return kit.isOneTime() && claims.hasClaimed(who, kit.id()) && !permissions.has(who, COOLDOWN_BYPASS_NODE);
    }

    public boolean isOnCooldown(PlayerRef who, KitDefinition kit) {
        return cooldowns.check(who, cooldownKind(who, kit)).isErr();
    }

    public boolean canAfford(PlayerRef who, KitDefinition kit) {
        if (!kit.hasCost() || economy.isEmpty()) {
            return true;
        }
        return economy.get().canAfford(who, kit.cost().amount(), kit.cost().currencyId());
    }

    private long resolveCooldownSeconds(PlayerRef who, KitDefinition kit) {
        long shortest = kit.cooldownSeconds();
        if (kit.permissionCooldowns().isEmpty()) {
            return shortest;
        }
        for (java.util.Map.Entry<String, java.time.Duration> entry :
                kit.permissionCooldowns().entrySet()) {
            String group = entry.getKey();
            if (permissions.has(who, "uxmessentials.kit.cooldown." + group)) {
                long durationSecs = entry.getValue().toSeconds();
                if (durationSecs < shortest) {
                    shortest = durationSecs;
                }
            }
        }
        return shortest;
    }
}

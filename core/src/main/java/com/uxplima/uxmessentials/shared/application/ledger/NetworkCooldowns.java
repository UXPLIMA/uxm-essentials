package com.uxplima.uxmessentials.shared.application.ledger;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;

import com.uxplima.uxmessentials.shared.application.permission.SharedPermissions;
import com.uxplima.uxmessentials.shared.application.port.CooldownTiers;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The {@link Cooldowns} a network uses: the same tiers, bypass nodes and stamp names as the PDC cooldowns, kept in
 * {@link NetworkClaims} so every server on the database sees one clock per player.
 *
 * <p>While a player's cooldowns are still being read, every check answers "wait" with {@link #UNREAD_WAIT}, so a
 * slow database never lets an action through that another server put on cooldown. A player nothing is held for
 * (not online here) passes, as with PDC. A system actor (the console, a command block, another plugin) passes and
 * stamps nothing: they all share one identity, and a stamp on it would hold every one of them back.
 */
public final class NetworkCooldowns implements Cooldowns {

    /** The wait a check reports for a player whose cooldowns are not read yet: about how long a read takes. */
    public static final Duration UNREAD_WAIT = Duration.ofSeconds(1);

    private static final String LABEL_SCOPE_PREFIX = "cooldown.";

    private final NetworkClaims claims;
    private final Permissions permissions;
    private final Clock clock;

    public NetworkCooldowns(NetworkClaims claims, Permissions permissions, Clock clock) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        if (permissions.has(who, kind.bypassNode())) {
            return Result.ok();
        }
        return gate(who, kind.stampScope());
    }

    @Override
    public void stamp(PlayerRef who, CooldownKind kind) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        if (permissions.has(who, kind.bypassNode())) {
            return;
        }
        long seconds = CooldownTiers.waitSeconds(permissions, who, kind);
        write(who, kind.stampScope(), seconds);
    }

    @Override
    public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(label, "label");
        if (permissions.has(who, SharedPermissions.cooldownBypass(label))) {
            return Result.ok();
        }
        return gate(who, LABEL_SCOPE_PREFIX + label);
    }

    @Override
    public void stampLabel(PlayerRef who, String label) {
        stampLabel(who, label, Duration.ZERO);
    }

    @Override
    public void stampLabel(PlayerRef who, String label, Duration configDefault) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(configDefault, "configDefault");
        if (permissions.has(who, SharedPermissions.cooldownBypass(label))) {
            return;
        }
        long seconds = CooldownTiers.labelWaitSeconds(permissions, who, label, configDefault.toSeconds());
        write(who, LABEL_SCOPE_PREFIX + label, seconds);
    }

    private Result<Unit, Duration> gate(PlayerRef who, String name) {
        if (who.isSystem()) {
            return Result.ok();
        }
        OptionalLong readyAt = claims.cooldownEnd(who.uuid(), CooldownScope.normalize(name));
        if (readyAt.isEmpty()) {
            return Result.err(UNREAD_WAIT);
        }
        long now = clock.millis();
        long end = readyAt.getAsLong();
        return now >= end ? Result.ok() : Result.err(Duration.ofMillis(end - now));
    }

    private void write(PlayerRef who, String name, long seconds) {
        if (seconds <= 0 || who.isSystem()) {
            return;
        }
        claims.stampCooldown(who.uuid(), CooldownScope.normalize(name), clock.millis() + seconds * 1000L);
    }
}

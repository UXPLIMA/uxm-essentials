package com.uxplima.uxmessentials.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/** Cooldowns stamped against a clock, each lasting the kind's default duration. */
public final class ClockCooldowns implements Cooldowns {

    private final Clock clock;
    private final Map<String, Instant> ends = new HashMap<>();

    public ClockCooldowns(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
        return checkLabel(who, kind.stampScope());
    }

    @Override
    public void stamp(PlayerRef who, CooldownKind kind) {
        ends.put(who.uuid() + kind.stampScope(), clock.instant().plus(kind.defaultDuration()));
    }

    @Override
    public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
        Instant end = ends.get(who.uuid() + label);
        if (end == null || !clock.instant().isBefore(end)) {
            return Result.ok();
        }
        return Result.err(Duration.between(clock.instant(), end));
    }

    @Override
    public void stampLabel(PlayerRef who, String label) {
        ends.put(who.uuid() + label, clock.instant());
    }
}

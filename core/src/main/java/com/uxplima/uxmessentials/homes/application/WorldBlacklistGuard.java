package com.uxplima.uxmessentials.homes.application;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.homes.application.port.SethomeGuard;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * A pure {@link SethomeGuard} that blocks placing a home in a configured disabled world. World names
 * are compared case-insensitively. The adapter supplies the set from the {@code disabled-worlds} config
 * key; an empty set means no worlds are disabled and every call returns {@link Result#ok()}.
 */
@NullMarked
public final class WorldBlacklistGuard implements SethomeGuard {

    private final Set<String> disabled;

    /**
     * @param disabledWorlds world names to block, matched case-insensitively; must not be {@code null}
     */
    public WorldBlacklistGuard(Set<String> disabledWorlds) {
        Objects.requireNonNull(disabledWorlds, "disabledWorlds");
        this.disabled =
                disabledWorlds.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(toUnmodifiableSet());
    }

    @Override
    public Result<Unit, HomeError> check(PlayerRef owner, HomeSlot slot, Position at) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(at, "at");
        return disabled.contains(at.world().name().toLowerCase(Locale.ROOT))
                ? Result.err(HomeError.WORLD_DISABLED)
                : Result.ok();
    }
}

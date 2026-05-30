package com.uxplima.uxmessentials.homes.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * An owner tried to create a home past their resolved limit. Published on the in-process bus so an
 * upsell or analytics listener can react (docs/10-feature-modules.md §15.1); the {@code /sethome} itself
 * is refused with {@code HomeError.LIMIT_REACHED}.
 *
 * @param owner the player who hit the cap
 * @param currentCount how many homes the owner already holds
 * @param limit the resolved cap they reached
 */
public record HomeLimitReached(PlayerRef owner, int currentCount, int limit) implements HomeEvent {

    public HomeLimitReached {
        Objects.requireNonNull(owner, "owner");
        if (currentCount < 0) {
            throw new IllegalArgumentException("currentCount must not be negative: " + currentCount);
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative: " + limit);
        }
    }
}

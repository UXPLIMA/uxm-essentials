package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * A parsed EssentialsX per-player jail sentence, source-shaped: the named jail the player is held in
 * ({@code jail}), the optional wall-clock expiry ({@code timestamps.jail}, epoch-millis), and the
 * optional online-time remaining ({@code timestamps.onlinejail}, millis). EssentialsX runs a jail by the
 * online clock by default and switches to the wall clock when an operator opts in; both timers can sit in
 * the same file, so the parser carries whichever is present and lets the mapper choose (docs/12-migration
 * §5.1). A permanent jail carries neither timer.
 *
 * @param jailName the named jail location the player is held in
 * @param wallClockExpiryEpochMillis the wall-clock expiry epoch-millis, empty unless opted in
 * @param onlineMillisRemaining the online-time remaining in millis, empty for a permanent/wall-clock jail
 */
@NullMarked
public record EssXJail(
        String jailName, Optional<Long> wallClockExpiryEpochMillis, Optional<Long> onlineMillisRemaining) {

    public EssXJail {
        Objects.requireNonNull(jailName, "jailName");
        Objects.requireNonNull(wallClockExpiryEpochMillis, "wallClockExpiryEpochMillis");
        Objects.requireNonNull(onlineMillisRemaining, "onlineMillisRemaining");
        if (jailName.isBlank()) {
            throw new IllegalArgumentException("jailName must not be blank");
        }
    }
}

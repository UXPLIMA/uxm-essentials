package com.uxplima.uxmessentials.vote.application;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.vote.domain.BroadcastChannel;
import com.uxplima.uxmessentials.vote.domain.BroadcastType;

/**
 * The config-derived broadcast settings, bundled into one value so {@link HandleVote}'s constructor stays
 * lean: the broadcast {@link BroadcastType type}, the per-player {@code cooldown} window used by
 * {@link BroadcastType#COOLDOWN_PER_PLAYER}, the set of {@link BroadcastChannel channels} every broadcast
 * is delivered on, and the lower-cased {@code blacklist} of voter names that are never announced.
 *
 * <p>The compact constructor takes defensive copies of the two sets and lower-cases the blacklist so a
 * blacklist check can compare against {@code voter.name().toLowerCase(Locale.ROOT)} directly.
 *
 * @param type the broadcast policy
 * @param cooldown the per-player cooldown window
 * @param channels the surfaces every broadcast is delivered on
 * @param blacklist the lower-cased voter names that are never announced
 */
public record BroadcastSettings(
        BroadcastType type, Duration cooldown, Set<BroadcastChannel> channels, Set<String> blacklist) {

    public BroadcastSettings {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(channels, "channels");
        Objects.requireNonNull(blacklist, "blacklist");
        channels = Set.copyOf(channels);
        blacklist =
                blacklist.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }
}

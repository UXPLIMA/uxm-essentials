package com.uxplima.uxmessentials.vote.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes one configured vote site: its canonical name, optional public URL, and the per-player
 * cooldown before the same player can vote on that site again. The cooldown is a domain concept —
 * the adapter reads it from config and passes the full catalog in at wire-time; no config type
 * appears here.
 *
 * @param name     the site key as it appears in Votifier events (case-preserved, non-blank)
 * @param url      the public vote URL shown to players in {@code /vote next} and {@code /vote}
 * @param cooldown how long after voting the site is unavailable to the same player; zero means
 *                 the site is always votable
 */
public record VoteSiteSpec(String name, Optional<String> url, Duration cooldown) {

    public VoteSiteSpec {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("site name must not be blank");
        }
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(cooldown, "cooldown");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must not be negative: " + cooldown);
        }
    }
}

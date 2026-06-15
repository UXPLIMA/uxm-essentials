package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.net.URI;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * The single-method seam {@link MojangSkinService} fetches over, so the service's two-step orchestration,
 * parsing and caching are unit-testable against a fake without a live network. The production realisation wraps
 * a JDK {@code HttpClient} GET; a test supplies a map of canned bodies.
 *
 * <p>The contract is fail-soft: {@link #get(URI)} returns the response body for a {@code 200}, and an empty
 * {@link Optional} for any non-{@code 200} status, timeout, or transport error (so a {@code 404} for an unknown
 * username is just an empty result the service treats as "no such player"). It never throws.
 */
@NullMarked
@FunctionalInterface
public interface HttpFetcher {

    /** The body of a {@code 200} GET to {@code uri}, or empty for a non-{@code 200}, timeout, or transport error. */
    Optional<String> get(URI uri);
}

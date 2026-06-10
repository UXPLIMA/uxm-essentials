package com.uxplima.uxmessentials.shared.adapter.outbound.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Version;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The async update checker: fetches the latest release tag from the operator-configured source and, when it is
 * newer than the running plugin version, records it so the join listener can notify operators.
 *
 * <p>The HTTP call is non-blocking — {@link HttpClient#sendAsync} on the JDK client's own executor, never a tick
 * thread, a {@code new Thread}, or {@code supplyAsync} (docs/02-concurrency, the same shape as the economy fraud
 * webhook). No request body, no auth header: a public release endpoint needs none and the checker never touches a
 * secret. The result handling stays off the response thread by re-entering through the {@link Scheduler} port; the
 * latest-found version lives in an {@link AtomicReference} the listener reads lock-free.
 *
 * <p>When the interval is non-zero the checker self-reschedules on the {@code Scheduler} (the announcer / salary
 * loop pattern), reading the live {@code running} flag each tick so it exits cleanly on disable. A network failure
 * or an unparseable body logs one warning and is otherwise swallowed — a missed check never crashes or blocks the
 * server.
 */
@NullMarked
public final class UpdateChecker {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final Scheduler scheduler;
    private final Logger log;
    private final HttpClient httpClient;
    private final Version current;
    private final UpdateCheckSettings settings;
    private final AtomicReference<Optional<Version>> available = new AtomicReference<>(Optional.empty());
    private final AtomicBoolean running = new AtomicBoolean(true);

    public UpdateChecker(Scheduler scheduler, Logger log, Version current, UpdateCheckSettings settings) {
        this(scheduler, log, current, settings, HttpClient.newBuilder().build());
    }

    UpdateChecker(
            Scheduler scheduler, Logger log, Version current, UpdateCheckSettings settings, HttpClient httpClient) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.current = Objects.requireNonNull(current, "current");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    /** The newer release found by the last successful check, or empty when up to date / not yet checked. */
    public Optional<Version> available() {
        return Objects.requireNonNull(available.get(), "available");
    }

    /** The running plugin version the fetched release is compared against. */
    public Version current() {
        return current;
    }

    /** Run the first check now (off-thread) and, when the interval is non-zero, arm the recurring re-check. */
    public void start() {
        check();
        if (settings.repeats()) {
            scheduleNext();
        }
    }

    /** Stop the self-rescheduling loop; the in-flight request, if any, completes and is then ignored. */
    public void stop() {
        running.set(false);
    }

    private void scheduleNext() {
        if (!running.get()) {
            return;
        }
        scheduler.asyncAfter(settings.interval(), this::tick);
    }

    private void tick() {
        if (!running.get()) {
            return;
        }
        check();
        scheduleNext();
    }

    /** Dispatch the non-blocking GET and handle the response off the response thread. */
    private void check() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(settings.sourceUrl()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            var unused = httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete(this::handle);
        } catch (RuntimeException malformed) {
            log.warn(
                    "update check skipped — invalid source URL '{}': {}",
                    settings.sourceUrl(),
                    String.valueOf(malformed.getMessage()));
        }
    }

    private void handle(HttpResponse<String> response, @Nullable Throwable failure) {
        if (!running.get()) {
            return;
        }
        if (failure != null) {
            log.warn("update check failed: {}", String.valueOf(failure.getMessage()));
            return;
        }
        if (response.statusCode() / 100 != 2) {
            log.warn("update check got HTTP {} from {}", response.statusCode(), settings.sourceUrl());
            return;
        }
        evaluate(response.body());
    }

    private void evaluate(String body) {
        Optional<Version> latest = ReleaseTag.from(body).flatMap(Version::parse);
        if (latest.isEmpty()) {
            log.warn("update check could not read a version from the source response");
            return;
        }
        Version newest = latest.get();
        if (newest.isNewerThan(current)) {
            available.set(Optional.of(newest));
            log.info("uxmEssentials update available: {} -> {}", current, newest);
        } else {
            available.set(Optional.empty());
        }
    }
}

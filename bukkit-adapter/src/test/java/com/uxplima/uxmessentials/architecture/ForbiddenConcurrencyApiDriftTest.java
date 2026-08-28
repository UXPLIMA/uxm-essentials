package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The thread-ownership guard (CLAUDE.md §3 "no CompletableFuture.supplyAsync / new Thread / BukkitRunnable",
 * docs/02-concurrency.md).
 *
 * <p><strong>The bug this freezes out.</strong> Each of these APIs quietly picks a thread the plugin does not own.
 * {@code CompletableFuture.supplyAsync(x)} lands on the common ForkJoinPool, which is sized for CPU work and is
 * shared with everything else in the JVM: one blocking database call parks a pool thread the whole server is
 * using. {@code new Thread(...)} creates something nothing will ever shut down, so a reload leaks it and a second
 * reload leaks another. {@code BukkitRunnable} is worse than either on Folia, where there is no single main thread
 * to return to and the task belongs to a region.
 *
 * <p><strong>The invariant.</strong> Scheduling goes through the injected {@code Scheduler} port
 * ({@code onRegion} / {@code onEntity} / {@code onGlobal} / {@code async}) and blocking work goes to an injected
 * {@code Executor} the module shuts down in {@code stop()}. The adapter implements the port on Paper's
 * region-aware schedulers; nothing above the adapter names a thread at all.
 *
 * <p>Comments are stripped before matching: this repository explains the rule in javadoc far more often than it
 * could ever break it, and a guard that cannot tell an explanation from a call is one nobody can keep green.
 */
class ForbiddenConcurrencyApiDriftTest {

    private static final Map<Pattern, String> FORBIDDEN = new LinkedHashMap<>();

    static {
        FORBIDDEN.put(
                Pattern.compile("CompletableFuture\\s*\\.\\s*(supplyAsync|runAsync)\\s*\\("),
                "runs on the shared common ForkJoinPool; use the Scheduler port's async(...) instead");
        FORBIDDEN.put(
                Pattern.compile("new\\s+Thread\\s*\\("), "creates a thread nothing shuts down; inject an Executor");
        FORBIDDEN.put(
                Pattern.compile("\\bBukkitRunnable\\b"),
                "is not Folia-safe and has no region to return to; use the Scheduler port");
        FORBIDDEN.put(
                Pattern.compile("\\bBukkitScheduler\\b"),
                "is the pre-Folia scheduler; use the Scheduler port (also fenced by the noClassDependsOnBukkitScheduler ArchUnit rule)");
    }

    @Test
    void noProductionCodePicksItsOwnThread() {
        List<String> offenders = new ArrayList<>();
        for (Path file : ProductionSources.files()) {
            String code = ProductionSources.code(ProductionSources.read(file));
            FORBIDDEN.forEach((pattern, why) -> {
                Matcher matcher = pattern.matcher(code);
                while (matcher.find()) {
                    offenders.add(ProductionSources.repoRoot().relativize(file) + ":"
                            + ProductionSources.lineOf(code, matcher.start()) + " "
                            + matcher.group().trim() + " "
                            + why);
                }
            });
        }
        assertThat(offenders)
                .as("every thread this plugin uses is one it owns and can shut down: the Scheduler port for"
                        + " scheduling, an injected Executor for blocking work (docs/02-concurrency.md)")
                .isEmpty();
    }
}

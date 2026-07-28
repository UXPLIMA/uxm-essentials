package com.uxplima.uxmessentials.shared.application.reload;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.jspecify.annotations.Nullable;

/**
 * One re-readable subsystem behind {@code /uxmess reload}. A task names what it re-reads and reports whether the
 * new values are live, so the command can tell an operator the truth about their edit instead of acknowledging it.
 *
 * <p>Two kinds of task exist. A <b>kernel</b> task ({@link #module()} empty) re-reads something the whole plugin
 * shares (the config tree, the message catalogs) and runs on every reload, scoped or not. A <b>module</b> task
 * names the module whose live state it rebuilds and runs only when that module is the target or the reload is
 * unscoped. A module with no task registered cannot apply config changes at runtime at all: the command reports
 * that as {@link ReloadStatus#RESTART_REQUIRED} rather than silently doing nothing.
 *
 * <p>This mirrors the {@code HealthCheck} seam: the bootstrap assembles the list from the subsystems that are
 * actually wired rather than putting a method on the {@code FeatureModule} contract that every module would have
 * to answer.
 *
 * <p><b>Resilience invariant.</b> A task re-reads live files that can be malformed, so {@link #run()} must convert
 * its own failure into a {@link ReloadStatus#FAILED} result rather than propagate: a thrown task would abort the
 * whole run and leave the operator with no report. Run a task through {@link #safe()} to enforce that.
 *
 * <p><b>Threading.</b> Reading config and catalog files is I/O, so the whole run is dispatched off the tick thread.
 * A task therefore re-reads its files and swaps an already-published reference; it must not touch the Bukkit API.
 */
public interface ReloadTask {

    /** A short, stable label for what this task re-reads (e.g. {@code "config"}, or the module's id). */
    String name();

    /** The module this task rebuilds, or empty when it is a kernel task that runs on every reload. */
    default Optional<ModuleId> module() {
        return Optional.empty();
    }

    /** Re-read the subsystem and report the outcome. Must not throw: convert failure into a result. */
    ReloadResult run();

    /**
     * A view of this task whose {@link #run()} can never throw: any exception it leaks is caught and reported as
     * {@link ReloadStatus#FAILED} carrying the exception's message, so one bad file never aborts the run.
     * Idempotent: wrapping a task that already honours the invariant changes nothing observable.
     */
    default ReloadTask safe() {
        ReloadTask delegate = this;
        return new ReloadTask() {
            @Override
            public String name() {
                return delegate.name();
            }

            @Override
            public Optional<ModuleId> module() {
                return delegate.module();
            }

            @Override
            public ReloadResult run() {
                try {
                    return delegate.run();
                } catch (RuntimeException reloadFailure) {
                    String detail = reloadFailure.getMessage();
                    return ReloadResult.failed("reload errored: " + (detail == null ? reloadFailure : detail));
                }
            }
        };
    }

    /** A kernel task named {@code name} that runs {@code action} and reports {@code message} once it is applied. */
    static ReloadTask kernel(String name, Runnable action, String message) {
        Objects.requireNonNull(name, "name");
        return of(name, null, action, message);
    }

    /** A task for {@code module} that runs {@code action} and reports {@code message} once it is applied. */
    static ReloadTask forModule(ModuleId module, Runnable action, String message) {
        Objects.requireNonNull(module, "module");
        return of(module.value(), module, action, message);
    }

    private static ReloadTask of(String name, @Nullable ModuleId module, Runnable action, String message) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(message, "message");
        return new ReloadTask() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Optional<ModuleId> module() {
                return Optional.ofNullable(module);
            }

            @Override
            public ReloadResult run() {
                action.run();
                return ReloadResult.applied(message);
            }
        };
    }
}

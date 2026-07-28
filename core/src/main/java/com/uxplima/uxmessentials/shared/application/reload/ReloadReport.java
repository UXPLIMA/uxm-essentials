package com.uxplima.uxmessentials.shared.application.reload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.jspecify.annotations.Nullable;

/**
 * The aggregate outcome of a {@code /uxmess reload} run: the named result of every step that ran, in run order,
 * plus the overall status folded as the worst severity across them. The command renders one line per entry.
 *
 * <p>A scoped run ({@code /uxmess reload <module>}) still runs every kernel task, because the config tree and the
 * message catalogs are shared and a module's own values live inside them; only the module tasks are filtered. A
 * scope naming a module that registered no task yields a single {@link ReloadStatus#RESTART_REQUIRED} entry, so
 * the operator is told plainly that their edit is not live rather than being shown an empty report.
 *
 * @param entries the per-step outcomes in run order
 */
public record ReloadReport(List<Entry> entries) {

    public ReloadReport {
        entries = List.copyOf(entries);
    }

    /** Run every task (each through {@link ReloadTask#safe()}) and collect the results into a report. */
    public static ReloadReport runAll(List<ReloadTask> tasks) {
        return run(tasks, null);
    }

    /**
     * Run the kernel tasks plus the tasks belonging to {@code only}, or every task when {@code only} is
     * {@code null}. A scope that matches no module task still reports the module, as restart-required.
     */
    public static ReloadReport run(List<ReloadTask> tasks, @Nullable ModuleId only) {
        Objects.requireNonNull(tasks, "tasks");
        List<Entry> results = new ArrayList<>();
        boolean scopeMatched = false;
        for (ReloadTask task : tasks) {
            if (!inScope(task, only)) {
                continue;
            }
            scopeMatched |= task.module().isPresent();
            ReloadTask safe = task.safe();
            results.add(new Entry(safe.name(), safe.run()));
        }
        if (only != null && !scopeMatched) {
            results.add(new Entry(
                    only.value(),
                    ReloadResult.restartRequired("no live-reload hook; restart the server to apply changes")));
        }
        return new ReloadReport(results);
    }

    private static boolean inScope(ReloadTask task, @Nullable ModuleId only) {
        return only == null || task.module().isEmpty() || task.module().get().equals(only);
    }

    /** The worst severity across every entry; {@link ReloadStatus#APPLIED} when the report is empty. */
    public ReloadStatus overall() {
        return entries.stream().map(entry -> entry.result().status()).reduce(ReloadStatus.APPLIED, ReloadStatus::worst);
    }

    /** Whether at least one step reported {@link ReloadStatus#FAILED}. */
    public boolean hasFailure() {
        return overall() == ReloadStatus.FAILED;
    }

    /**
     * One line of a report: the step name and its result.
     *
     * @param name the step's label
     * @param result the step's outcome
     */
    public record Entry(String name, ReloadResult result) {
        public Entry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(result, "result");
        }
    }
}

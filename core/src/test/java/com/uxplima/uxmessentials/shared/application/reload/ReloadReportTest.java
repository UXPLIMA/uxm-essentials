package com.uxplima.uxmessentials.shared.application.reload;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * The reload report's contract: every kernel task runs on every reload, a scoped run narrows only the module
 * tasks, a scope with no registered task is reported as restart-required rather than silently doing nothing, and
 * a task that throws is contained instead of aborting the run.
 */
class ReloadReportTest {

    private static final ModuleId SURVIVAL = ModuleId.of("survival");
    private static final ModuleId ECONOMY = ModuleId.of("economy");

    @Test
    void runsEveryTaskWhenUnscoped() {
        List<String> ran = new ArrayList<>();

        ReloadReport report = ReloadReport.runAll(List.of(
                ReloadTask.kernel("config", () -> ran.add("config"), "config re-read"),
                ReloadTask.forModule(SURVIVAL, () -> ran.add("survival"), "mechanics re-read")));

        assertThat(ran).containsExactly("config", "survival");
        assertThat(report.entries()).extracting(ReloadReport.Entry::name).containsExactly("config", "survival");
        assertThat(report.overall()).isEqualTo(ReloadStatus.APPLIED);
        assertThat(report.hasFailure()).isFalse();
    }

    @Test
    void keepsKernelTasksButNarrowsModuleTasksWhenScoped() {
        List<String> ran = new ArrayList<>();

        // The config tree is shared, so a scoped reload still re-reads it: the module's own values live in there.
        ReloadReport.run(
                List.of(
                        ReloadTask.kernel("config", () -> ran.add("config"), "config re-read"),
                        ReloadTask.forModule(SURVIVAL, () -> ran.add("survival"), "mechanics re-read"),
                        ReloadTask.forModule(ECONOMY, () -> ran.add("economy"), "economy re-read")),
                SURVIVAL);

        assertThat(ran).containsExactly("config", "survival");
    }

    @Test
    void reportsAModuleWithNoTaskAsRestartRequired() {
        ReloadReport report = ReloadReport.run(
                List.of(ReloadTask.kernel("config", () -> {}, "config re-read")), ModuleId.of("worlds"));

        // The operator must not be told a change is live when nothing rebuilt that module's wiring.
        assertThat(report.entries()).hasSize(2);
        assertThat(report.entries().get(1).name()).isEqualTo("worlds");
        assertThat(report.entries().get(1).result().status()).isEqualTo(ReloadStatus.RESTART_REQUIRED);
        assertThat(report.overall()).isEqualTo(ReloadStatus.RESTART_REQUIRED);
        assertThat(report.hasFailure()).isFalse();
    }

    @Test
    void containsAThrowingTaskAsAFailedEntryAndKeepsGoing() {
        List<String> ran = new ArrayList<>();

        ReloadReport report = ReloadReport.runAll(
                List.of(throwingTask(), ReloadTask.kernel("messages", () -> ran.add("messages"), "catalogs re-read")));

        assertThat(ran).containsExactly("messages");
        assertThat(report.entries().get(0).result().status()).isEqualTo(ReloadStatus.FAILED);
        assertThat(report.entries().get(0).result().message()).contains("malformed HOCON");
        assertThat(report.hasFailure()).isTrue();
    }

    @Test
    void foldsTheWorstStatusAcrossEntries() {
        assertThat(ReloadStatus.APPLIED.worst(ReloadStatus.RESTART_REQUIRED)).isEqualTo(ReloadStatus.RESTART_REQUIRED);
        assertThat(ReloadStatus.FAILED.worst(ReloadStatus.RESTART_REQUIRED)).isEqualTo(ReloadStatus.FAILED);
        assertThat(ReloadStatus.APPLIED.worst(ReloadStatus.APPLIED)).isEqualTo(ReloadStatus.APPLIED);
    }

    private static ReloadTask throwingTask() {
        return new ReloadTask() {
            @Override
            public String name() {
                return "config";
            }

            @Override
            public Optional<ModuleId> module() {
                return Optional.empty();
            }

            @Override
            public ReloadResult run() {
                throw new IllegalStateException("malformed HOCON");
            }
        };
    }
}

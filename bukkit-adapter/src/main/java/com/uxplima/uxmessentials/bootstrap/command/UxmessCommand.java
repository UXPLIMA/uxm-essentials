package com.uxplima.uxmessentials.bootstrap.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthReport;
import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import com.uxplima.uxmessentials.shared.application.health.RepairResult;
import com.uxplima.uxmessentials.shared.application.health.RepairStatus;
import com.uxplima.uxmessentials.shared.application.health.RepairableHealthCheck;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.reload.ReloadReport;
import com.uxplima.uxmessentials.shared.application.reload.ReloadResult;
import com.uxplima.uxmessentials.shared.application.reload.ReloadStatus;
import com.uxplima.uxmessentials.shared.application.reload.ReloadTask;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@code /uxmess} operator root (aliases {@code /uxmessentials}, {@code /uxe}).
 *
 * <p>{@code status} and {@code help} read the live {@link ModuleRegistry}. Output is deliberately plain,
 * operator-facing diagnostic text: the i18n {@code MessageKey} catalog backs player-facing feature messages.
 *
 * <p>{@code reload} re-reads every registered {@link ReloadTask} and reports, per step, whether the new values are
 * live or waiting on a restart. That distinction is the point of the subcommand: most modules build their adapters
 * and listeners once at enable, so a config edit they cannot apply at runtime must be reported as restart-bound
 * rather than acknowledged as done. Re-reading files is I/O, so the run is dispatched off the tick thread and the
 * lines bridge back to the global region, exactly like {@code doctor}.
 *
 * <p>{@code doctor} goes deeper than {@code status}: it runs the wired {@link HealthCheck}s (a database
 * liveness probe, the economy-provider ownership check, the soft-depend presence/reachability scan, and the
 * threading/update lines) and prints each as {@code OK}/{@code WARN}/{@code FAIL}. The checks do I/O, so the
 * run is dispatched off the tick thread through the kernel {@link Scheduler} and the rendered lines bridge back
 * to the global region for delivery, exactly as the other off-tick bootstrap commands reply.
 */
@NullMarked
public final class UxmessCommand implements CommandRegistration, AutoCloseable {

    private static final String ROOT_LITERAL = "uxmess";
    private static final List<String> ALIASES = List.of("uxmessentials", "uxe");
    private static final String DESCRIPTION = "uxmEssentials administration root.";

    private static final String PERMISSION_ADMIN = "uxmessentials.admin";
    private static final String PERMISSION_RELOAD = "uxmessentials.admin.reload";
    private static final String PERMISSION_DOCTOR_REPAIR = "uxmessentials.admin.doctor.repair";

    /** The arguments of the import line, which are command syntax and the same in every language. */
    private static final String IMPORT_ARGUMENTS = "<source> [--dry-run]";

    /** Escapes data, a module id or a check's own message, before it is placed in a catalogue line. */
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ModuleRegistry registry;
    private final ConfigStore config;
    private final MigrationImportNode importNode;
    private final GuiSubcommand guiNode;
    private final PermissionsSubcommand permissionsNode;
    private final PlaceholdersSubcommand placeholdersNode;
    private final Scheduler scheduler;
    private final List<HealthCheck> healthChecks;
    private final List<ReloadTask> reloadTasks;
    private final AtomicBoolean reloadInProgress = new AtomicBoolean();
    private final AtomicBoolean doctorInProgress = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CommandFeedback feedback;

    public UxmessCommand(
            ModuleRegistry registry,
            ConfigStore config,
            MigrationImportNode importNode,
            GuiSubcommand guiNode,
            PermissionsSubcommand permissionsNode,
            PlaceholdersSubcommand placeholdersNode,
            Scheduler scheduler,
            List<HealthCheck> healthChecks,
            List<ReloadTask> reloadTasks,
            Messages messages) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
        this.importNode = Objects.requireNonNull(importNode, "importNode");
        this.guiNode = Objects.requireNonNull(guiNode, "guiNode");
        this.permissionsNode = Objects.requireNonNull(permissionsNode, "permissionsNode");
        this.placeholdersNode = Objects.requireNonNull(placeholdersNode, "placeholdersNode");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.healthChecks = List.copyOf(Objects.requireNonNull(healthChecks, "healthChecks"));
        this.reloadTasks = List.copyOf(Objects.requireNonNull(reloadTasks, "reloadTasks"));
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(ROOT_LITERAL)
                .requires(src -> src.getSender().hasPermission(PERMISSION_ADMIN))
                .executes(this::runHelp)
                .then(Commands.literal("status").executes(this::runStatus))
                .then(doctorNode())
                .then(Commands.literal("help").executes(this::runHelp))
                .then(guiNode.build())
                .then(reloadNode())
                .then(importNode.build())
                .then(permissionsNode.build())
                .then(placeholdersNode.build())
                .build();
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> doctorNode() {
        return Commands.literal("doctor")
                .executes(this::runDoctor)
                .then(Commands.literal("repair")
                        .requires(src -> src.getSender().hasPermission(PERMISSION_DOCTOR_REPAIR))
                        .executes(this::runRepairPreview)
                        .then(Commands.literal("confirm").executes(this::runRepairConfirmed)));
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<String> aliases() {
        return ALIASES;
    }

    /**
     * Bare {@code /uxmess} opens the management hub, the same screen {@code /uxmess gui} opens. Installed on
     * the root only when the command's catalog {@code gui} flag is on; with it off the root keeps its
     * {@code runHelp} fallback, which the {@code GuiRootBinding} leaves in place rather than replacing.
     */
    @Override
    public java.util.Optional<Command<CommandSourceStack>> guiRoot() {
        return java.util.Optional.of(guiNode.opener());
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> reloadNode() {
        return Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission(PERMISSION_RELOAD))
                .executes(this::runReloadAll)
                .then(Commands.argument("module", Args.token())
                        .suggests((context, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            registry.all().stream()
                                    .map(module -> module.id().value())
                                    .filter(id -> id.startsWith(remaining))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(this::runReloadOne));
    }

    private int runStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        feedback.send(sender, SharedMessageKey.ADMIN_STATUS_HEADER);
        List<FeatureModule> modules = registry.all();
        if (modules.isEmpty()) {
            feedback.send(sender, SharedMessageKey.ADMIN_STATUS_NONE);
            return Command.SINGLE_SUCCESS;
        }
        for (FeatureModule module : modules) {
            sendModuleLine(sender, module);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runDoctor(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        if (closed.get()) {
            feedback.send(sender, SharedMessageKey.ADMIN_CLOSED);
            return 0;
        }
        if (!doctorInProgress.compareAndSet(false, true)) {
            feedback.send(sender, SharedMessageKey.ADMIN_DOCTOR_BUSY);
            return 0;
        }
        feedback.send(sender, SharedMessageKey.ADMIN_DOCTOR_HEADER);
        feedback.send(sender, SharedMessageKey.ADMIN_DOCTOR_RUNNING);
        // The database probe acquires a connection, so the whole run goes off-tick; the rendered lines bridge
        // back to the global region for delivery, mirroring the other off-tick bootstrap commands.
        try {
            scheduler.async(() -> {
                if (closed.get()) {
                    doctorInProgress.set(false);
                    return;
                }
                HealthReport report = HealthReport.run(healthChecks);
                scheduleDoctorRender(() -> renderReport(sender, report));
            });
        } catch (RuntimeException schedulingFailure) {
            doctorInProgress.set(false);
            throw schedulingFailure;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runRepairPreview(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        feedback.send(sender, SharedMessageKey.ADMIN_REPAIR_HEADER);
        feedback.send(sender, SharedMessageKey.ADMIN_REPAIR_PREVIEW);
        return Command.SINGLE_SUCCESS;
    }

    private int runRepairConfirmed(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        if (closed.get()) {
            feedback.send(sender, SharedMessageKey.ADMIN_CLOSED);
            return 0;
        }
        if (!doctorInProgress.compareAndSet(false, true)) {
            feedback.send(sender, SharedMessageKey.ADMIN_DOCTOR_BUSY);
            return 0;
        }
        feedback.send(sender, SharedMessageKey.ADMIN_REPAIR_HEADER);
        feedback.send(sender, SharedMessageKey.ADMIN_REPAIR_STARTED);
        try {
            scheduler.async(() -> {
                if (closed.get()) {
                    doctorInProgress.set(false);
                    return;
                }
                List<RepairEntry> results = healthChecks.stream()
                        .filter(RepairableHealthCheck.class::isInstance)
                        .map(RepairableHealthCheck.class::cast)
                        .map(check -> new RepairEntry(check.name(), check.repairSafely()))
                        .toList();
                scheduleDoctorRender(() -> renderRepairs(sender, results));
            });
        } catch (RuntimeException schedulingFailure) {
            doctorInProgress.set(false);
            throw schedulingFailure;
        }
        return Command.SINGLE_SUCCESS;
    }

    private void scheduleDoctorRender(Runnable render) {
        try {
            scheduler.onGlobal(() -> {
                try {
                    if (!closed.get()) {
                        render.run();
                    }
                } finally {
                    doctorInProgress.set(false);
                }
            });
        } catch (RuntimeException schedulingFailure) {
            doctorInProgress.set(false);
            throw schedulingFailure;
        }
    }

    private void renderReport(CommandSender sender, HealthReport report) {
        for (HealthReport.Entry entry : report.entries()) {
            sendCheckLine(sender, entry);
        }
        if (report.hasFailure()) {
            feedback.send(sender, SharedMessageKey.ADMIN_DOCTOR_FAILURE);
        }
        long ok = healthCount(report, HealthStatus.OK);
        long warn = healthCount(report, HealthStatus.WARN);
        long failed = healthCount(report, HealthStatus.FAIL);
        String result = failed > 0 ? "FAILED" : warn > 0 ? "WARN" : "SUCCESS";
        result(sender, result + " command=doctor ok=" + ok + " warn=" + warn + " failed=" + failed);
    }

    private static long healthCount(HealthReport report, HealthStatus status) {
        return report.entries().stream()
                .filter(entry -> entry.result().status() == status)
                .count();
    }

    private void renderRepairs(CommandSender sender, List<RepairEntry> entries) {
        for (RepairEntry entry : entries) {
            SharedMessageKey key =
                    switch (entry.result().status()) {
                        case REPAIRED -> SharedMessageKey.ADMIN_CHECK_REPAIRED;
                        case UNCHANGED -> SharedMessageKey.ADMIN_CHECK_UNCHANGED;
                        case FAILED -> SharedMessageKey.ADMIN_CHECK_FAIL;
                    };
            checkLine(sender, key, entry.name(), entry.result().message());
        }
        long repaired = repairCount(entries, RepairStatus.REPAIRED);
        long unchanged = repairCount(entries, RepairStatus.UNCHANGED);
        long failed = repairCount(entries, RepairStatus.FAILED);
        String result = failed > 0 ? "FAILED" : "SUCCESS";
        result(
                sender,
                result + " command=doctor.repair repaired=" + repaired + " unchanged=" + unchanged + " failed="
                        + failed);
    }

    private static long repairCount(List<RepairEntry> entries, RepairStatus status) {
        return entries.stream()
                .filter(entry -> entry.result().status() == status)
                .count();
    }

    private void sendCheckLine(CommandSender sender, HealthReport.Entry entry) {
        SharedMessageKey key =
                switch (entry.result().status()) {
                    case OK -> SharedMessageKey.ADMIN_CHECK_OK;
                    case WARN -> SharedMessageKey.ADMIN_CHECK_WARN;
                    case FAIL -> SharedMessageKey.ADMIN_CHECK_FAIL;
                };
        checkLine(sender, key, entry.name(), entry.result().message());
    }

    /** One check, repair or reload step: its name and its own message are data, so both are escaped. */
    private void checkLine(CommandSender sender, SharedMessageKey key, String name, String message) {
        feedback.send(sender, key, Map.of("name", escape(name), "message", escape(message)));
    }

    /**
     * The machine-readable summary a script greps for. It is a format, not a sentence, so it is the same in every
     * language: {@code Result: <state> command=<name> <counter>=<n>}.
     */
    private void result(CommandSender sender, String summary) {
        feedback.send(sender, SharedMessageKey.ADMIN_RESULT, Map.of("line", escape(resultLine(summary))));
    }

    private static String resultLine(String summary) {
        return "Result: " + summary;
    }

    private int runHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        feedback.send(sender, SharedMessageKey.ADMIN_HELP_HEADER);
        feedback.send(sender, SharedMessageKey.ADMIN_HELP_STATUS);
        feedback.send(sender, SharedMessageKey.ADMIN_HELP_DOCTOR);
        feedback.send(sender, SharedMessageKey.ADMIN_HELP_HELP);
        feedback.send(sender, SharedMessageKey.ADMIN_HELP_GUI);
        feedback.send(sender, SharedMessageKey.ADMIN_HELP_RELOAD);
        feedback.send(sender, SharedMessageKey.ADMIN_HELP_IMPORT, Map.of("args", escape(IMPORT_ARGUMENTS)));
        feedback.send(sender, SharedMessageKey.ADMIN_HELP_PERMISSIONS);
        feedback.send(sender, SharedMessageKey.ADMIN_HELP_PLACEHOLDERS);
        return Command.SINGLE_SUCCESS;
    }

    private int runReloadAll(CommandContext<CommandSourceStack> ctx) {
        return runReload(Sender.audience(ctx.getSource()), null);
    }

    private int runReloadOne(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        String requested = ctx.getArgument("module", String.class);
        FeatureModule module = resolve(requested);
        if (module == null) {
            feedback.send(sender, SharedMessageKey.ADMIN_RELOAD_UNKNOWN, Map.of("module", escape(requested)));
            return 0;
        }
        return runReload(sender, module.id());
    }

    /**
     * Re-read the registered subsystems and report what actually took effect. Every step is file I/O, so the run is
     * dispatched off the tick thread exactly like {@code doctor} and the rendered lines bridge back to the global
     * region for delivery.
     */
    private int runReload(CommandSender sender, @Nullable ModuleId only) {
        if (closed.get()) {
            feedback.send(sender, SharedMessageKey.ADMIN_CLOSED);
            return 0;
        }
        if (!reloadInProgress.compareAndSet(false, true)) {
            feedback.send(sender, SharedMessageKey.ADMIN_RELOAD_BUSY);
            return 0;
        }
        feedback.send(sender, SharedMessageKey.ADMIN_RELOAD_HEADER);
        feedback.send(sender, SharedMessageKey.ADMIN_RELOAD_STARTED);
        long startedNanos = System.nanoTime();
        Map<ModuleId, Boolean> enabledBefore = enabledStates();
        try {
            scheduler.async(() -> {
                if (closed.get()) {
                    reloadInProgress.set(false);
                    return;
                }
                ReloadReport report = withRestartBoundCoverage(
                        withStructuralChanges(ReloadReport.run(reloadTasks, only), enabledBefore, only), only);
                long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
                try {
                    scheduler.onGlobal(() -> {
                        try {
                            if (!closed.get()) {
                                renderReload(sender, report, elapsedMillis);
                            }
                        } finally {
                            reloadInProgress.set(false);
                        }
                    });
                } catch (RuntimeException schedulingFailure) {
                    reloadInProgress.set(false);
                    throw schedulingFailure;
                }
            });
        } catch (RuntimeException schedulingFailure) {
            reloadInProgress.set(false);
            throw schedulingFailure;
        }
        return Command.SINGLE_SUCCESS;
    }

    private Map<ModuleId, Boolean> enabledStates() {
        Map<ModuleId, Boolean> states = new LinkedHashMap<>();
        for (FeatureModule module : registry.all()) {
            states.put(module.id(), module.enabled(config));
        }
        return Map.copyOf(states);
    }

    /**
     * Module start/stop, listener ownership and Brigadier publication are structural. The shared config snapshot may
     * observe an edited enabled flag during reload, but changing the already-wired runtime in place is deliberately
     * unsupported; surface that boundary as restart-required instead of claiming the module was toggled.
     */
    private ReloadReport withStructuralChanges(
            ReloadReport report, Map<ModuleId, Boolean> enabledBefore, @Nullable ModuleId only) {
        List<ReloadReport.Entry> entries = new ArrayList<>(report.entries());
        for (FeatureModule module : registry.all()) {
            if (only != null && !only.equals(module.id())) {
                continue;
            }
            Boolean before = enabledBefore.get(module.id());
            boolean after = module.enabled(config);
            if (before != null && before.booleanValue() != after) {
                entries.add(new ReloadReport.Entry(
                        module.id().value() + ".enabled",
                        ReloadResult.restartRequired(
                                "enabled state changed; restart required to rebuild commands, listeners and tasks")));
            }
        }
        return new ReloadReport(entries);
    }

    /** Add the aggregate restart boundary to the report itself so the final status counts agree with its detail. */
    private ReloadReport withRestartBoundCoverage(ReloadReport report, @Nullable ModuleId only) {
        if (only != null) {
            return report;
        }
        long waiting = registry.all().stream()
                .filter(module -> module.enabled(config))
                .filter(module -> reloadTasks.stream()
                        .noneMatch(task -> task.module()
                                .filter(id -> id.equals(module.id()))
                                .isPresent()))
                .count();
        if (waiting == 0) {
            return report;
        }
        List<ReloadReport.Entry> entries = new ArrayList<>(report.entries());
        entries.add(new ReloadReport.Entry(
                "module wiring",
                ReloadResult.restartRequired(
                        waiting + " enabled module(s) have no live hook; restart to apply structural config changes")));
        return new ReloadReport(entries);
    }

    private void renderReload(CommandSender sender, ReloadReport report, long elapsedMillis) {
        for (ReloadReport.Entry entry : report.entries()) {
            SharedMessageKey key =
                    switch (entry.result().status()) {
                        case APPLIED -> SharedMessageKey.ADMIN_CHECK_OK;
                        case RESTART_REQUIRED -> SharedMessageKey.ADMIN_CHECK_RESTART;
                        case FAILED -> SharedMessageKey.ADMIN_CHECK_FAIL;
                    };
            checkLine(sender, key, entry.name(), entry.result().message());
        }
        if (report.hasFailure()) {
            feedback.send(sender, SharedMessageKey.ADMIN_RELOAD_FAILURE);
        }
        long applied = count(report, ReloadStatus.APPLIED);
        long restart = count(report, ReloadStatus.RESTART_REQUIRED);
        long failed = count(report, ReloadStatus.FAILED);
        feedback.send(
                sender,
                SharedMessageKey.ADMIN_RELOAD_SUMMARY,
                Map.of(
                        "applied", String.valueOf(applied),
                        "restart", String.valueOf(restart),
                        "failed", String.valueOf(failed),
                        "ms", String.valueOf(elapsedMillis)));
        String result = failed > 0 ? "FAILED" : restart > 0 ? "RESTART_REQUIRED" : "SUCCESS";
        result(
                sender,
                result + " command=reload applied=" + applied + " restart=" + restart + " failed=" + failed
                        + " elapsedMs=" + elapsedMillis);
    }

    private static long count(ReloadReport report, ReloadStatus status) {
        return report.entries().stream()
                .filter(entry -> entry.result().status() == status)
                .count();
    }

    private void sendModuleLine(CommandSender sender, FeatureModule module) {
        SharedMessageKey key =
                module.enabled(config) ? SharedMessageKey.ADMIN_STATUS_ON : SharedMessageKey.ADMIN_STATUS_OFF;
        feedback.send(sender, key, Map.of("module", escape(module.id().value())));
    }

    private static String escape(String text) {
        return MINI_MESSAGE.escapeTags(text, StyleTags.resolver());
    }

    private @Nullable FeatureModule resolve(String requested) {
        try {
            return registry.byId(ModuleId.of(requested)).orElse(null);
        } catch (IllegalArgumentException invalidId) {
            // A malformed id (uppercase, spaces) can never match a registered module.
            return null;
        }
    }

    /** Prevent off-tick doctor/reload completions from touching the server after plugin disable. */
    @Override
    public void close() {
        closed.set(true);
    }

    private record RepairEntry(String name, RepairResult result) {}
}

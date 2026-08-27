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
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthReport;
import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import com.uxplima.uxmessentials.shared.application.health.RepairResult;
import com.uxplima.uxmessentials.shared.application.health.RepairStatus;
import com.uxplima.uxmessentials.shared.application.health.RepairableHealthCheck;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.reload.ReloadReport;
import com.uxplima.uxmessentials.shared.application.reload.ReloadResult;
import com.uxplima.uxmessentials.shared.application.reload.ReloadStatus;
import com.uxplima.uxmessentials.shared.application.reload.ReloadTask;
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

    private static final String STATUS_HEADER = "uxmEssentials: modules";
    private static final String STATUS_NO_MODULES = "No feature modules are registered yet.";
    private static final String HELP_HEADER = "uxmEssentials commands:";
    private static final String HELP_STATUS = "/uxmess status: list modules and their enable state";
    private static final String HELP_DOCTOR =
            "/uxmess doctor [repair [confirm]]: inspect health; confirmed repair fixes only safe orphan data";
    private static final String HELP_HELP = "/uxmess help: show this help";
    private static final String HELP_GUI = "/uxmess gui: open the module management hub";
    private static final String HELP_RELOAD =
            "/uxmess reload [module]: re-read the config and message files, all or scoped to one module";
    private static final String HELP_IMPORT = "/uxmess import <source> [--dry-run]: import legacy data";
    private static final String HELP_PERMISSIONS =
            "/uxmess permissions [area] [page]: read the permission catalogue, export writes it to a file";
    private static final String HELP_PLACEHOLDERS =
            "/uxmess placeholders [area] [page]: read the placeholder catalogue, export writes it to a file";

    private static final String DOCTOR_HEADER = "uxmEssentials: health checks";
    private static final String DOCTOR_RUNNING = "Running health checks off-tick…";
    private static final String DOCTOR_FAILURE = "One or more checks FAILED; see the lines above.";
    private static final String DOCTOR_RUNNING_ALREADY =
            "A doctor or repair run is already active; this request was not started.";
    private static final String DOCTOR_REPAIR_PREVIEW =
            "Preview only: no data changed. Review /uxmess doctor, then run /uxmess doctor repair confirm.";
    private static final String DOCTOR_REPAIR_HEADER = "uxmEssentials: safe data repair";
    private static final String DOCTOR_REPAIR_STARTED =
            "Running confirmed repairs off-tick; parent and missing-world location records are retained.";
    private static final String DOCTOR_CLOSED = "Plugin shutdown is in progress; this request was not started.";
    private static final String DOCTOR_OK = "[OK] ";
    private static final String DOCTOR_WARN = "[WARN] ";
    private static final String DOCTOR_FAIL = "[FAIL] ";
    private static final String DOCTOR_REPAIRED = "[REPAIRED] ";
    private static final String DOCTOR_UNCHANGED = "[UNCHANGED] ";
    private static final String WARN = "<#ffcc66>";
    private static final String RELOAD_HEADER = "uxmEssentials reload";
    private static final String RELOAD_RUNNING = "Reload is already running; this request was not started.";
    private static final String RELOAD_STARTED = "Reading and validating files off-tick…";
    private static final String RELOAD_RESTART = "[RESTART] ";
    private static final String RELOAD_FAILURE =
            "One or more steps FAILED; each failed step kept its last-known-good values.";
    private static final String RELOAD_UNKNOWN = "Unknown module: ";
    private static final String STATE_ENABLED = ": enabled";
    private static final String STATE_DISABLED = ": disabled";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String HEADER = "<gradient:#4aa3ff:#9b6bff>";
    private static final String HEADER_END = "</gradient>";
    private static final String BODY = "<#aeb8c4>";
    private static final String SUCCESS = "<#57e389>";
    private static final String ERROR = "<#ff6b6b>";

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

    public UxmessCommand(
            ModuleRegistry registry,
            ConfigStore config,
            MigrationImportNode importNode,
            GuiSubcommand guiNode,
            PermissionsSubcommand permissionsNode,
            PlaceholdersSubcommand placeholdersNode,
            Scheduler scheduler,
            List<HealthCheck> healthChecks,
            List<ReloadTask> reloadTasks) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
        this.importNode = Objects.requireNonNull(importNode, "importNode");
        this.guiNode = Objects.requireNonNull(guiNode, "guiNode");
        this.permissionsNode = Objects.requireNonNull(permissionsNode, "permissionsNode");
        this.placeholdersNode = Objects.requireNonNull(placeholdersNode, "placeholdersNode");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.healthChecks = List.copyOf(Objects.requireNonNull(healthChecks, "healthChecks"));
        this.reloadTasks = List.copyOf(Objects.requireNonNull(reloadTasks, "reloadTasks"));
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
                .then(Commands.argument("module", StringArgumentType.word())
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
        CommandSender sender = ctx.getSource().getSender();
        sendHeader(sender, STATUS_HEADER);
        List<FeatureModule> modules = registry.all();
        if (modules.isEmpty()) {
            sendBody(sender, STATUS_NO_MODULES);
            return Command.SINGLE_SUCCESS;
        }
        for (FeatureModule module : modules) {
            sendModuleLine(sender, module);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runDoctor(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (closed.get()) {
            send(sender, ERROR, DOCTOR_CLOSED);
            return 0;
        }
        if (!doctorInProgress.compareAndSet(false, true)) {
            send(sender, WARN, DOCTOR_RUNNING_ALREADY);
            return 0;
        }
        sendHeader(sender, DOCTOR_HEADER);
        sendBody(sender, DOCTOR_RUNNING);
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
        CommandSender sender = ctx.getSource().getSender();
        sendHeader(sender, DOCTOR_REPAIR_HEADER);
        sendBody(sender, DOCTOR_REPAIR_PREVIEW);
        return Command.SINGLE_SUCCESS;
    }

    private int runRepairConfirmed(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (closed.get()) {
            send(sender, ERROR, DOCTOR_CLOSED);
            return 0;
        }
        if (!doctorInProgress.compareAndSet(false, true)) {
            send(sender, WARN, DOCTOR_RUNNING_ALREADY);
            return 0;
        }
        sendHeader(sender, DOCTOR_REPAIR_HEADER);
        sendBody(sender, DOCTOR_REPAIR_STARTED);
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
            send(sender, ERROR, DOCTOR_FAILURE);
        }
        long ok = healthCount(report, HealthStatus.OK);
        long warn = healthCount(report, HealthStatus.WARN);
        long failed = healthCount(report, HealthStatus.FAIL);
        String result = failed > 0 ? "FAILED" : warn > 0 ? "WARN" : "SUCCESS";
        send(
                sender,
                failed > 0 ? ERROR : warn > 0 ? WARN : SUCCESS,
                "Result: " + result + " command=doctor ok=" + ok + " warn=" + warn + " failed=" + failed);
    }

    private static long healthCount(HealthReport report, HealthStatus status) {
        return report.entries().stream()
                .filter(entry -> entry.result().status() == status)
                .count();
    }

    private void renderRepairs(CommandSender sender, List<RepairEntry> entries) {
        for (RepairEntry entry : entries) {
            String tag =
                    switch (entry.result().status()) {
                        case REPAIRED -> DOCTOR_REPAIRED;
                        case UNCHANGED -> DOCTOR_UNCHANGED;
                        case FAILED -> DOCTOR_FAIL;
                    };
            String colour =
                    switch (entry.result().status()) {
                        case REPAIRED -> SUCCESS;
                        case UNCHANGED -> BODY;
                        case FAILED -> ERROR;
                    };
            send(sender, colour, tag + entry.name() + ": " + entry.result().message());
        }
        long repaired = repairCount(entries, RepairStatus.REPAIRED);
        long unchanged = repairCount(entries, RepairStatus.UNCHANGED);
        long failed = repairCount(entries, RepairStatus.FAILED);
        String result = failed > 0 ? "FAILED" : "SUCCESS";
        send(
                sender,
                failed > 0 ? ERROR : SUCCESS,
                "Result: " + result + " command=doctor.repair repaired=" + repaired + " unchanged=" + unchanged
                        + " failed=" + failed);
    }

    private static long repairCount(List<RepairEntry> entries, RepairStatus status) {
        return entries.stream()
                .filter(entry -> entry.result().status() == status)
                .count();
    }

    private static void sendCheckLine(CommandSender sender, HealthReport.Entry entry) {
        HealthStatus status = entry.result().status();
        String line = tag(status) + entry.name() + ": " + entry.result().message();
        send(sender, palette(status), line);
    }

    private static String tag(HealthStatus status) {
        return switch (status) {
            case OK -> DOCTOR_OK;
            case WARN -> DOCTOR_WARN;
            case FAIL -> DOCTOR_FAIL;
        };
    }

    private static String palette(HealthStatus status) {
        return switch (status) {
            case OK -> SUCCESS;
            case WARN -> WARN;
            case FAIL -> ERROR;
        };
    }

    private int runHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        sendHeader(sender, HELP_HEADER);
        sendBody(sender, HELP_STATUS);
        sendBody(sender, HELP_DOCTOR);
        sendBody(sender, HELP_HELP);
        sendBody(sender, HELP_GUI);
        sendBody(sender, HELP_RELOAD);
        sendBody(sender, HELP_IMPORT);
        sendBody(sender, HELP_PERMISSIONS);
        sendBody(sender, HELP_PLACEHOLDERS);
        return Command.SINGLE_SUCCESS;
    }

    private int runReloadAll(CommandContext<CommandSourceStack> ctx) {
        return runReload(ctx.getSource().getSender(), null);
    }

    private int runReloadOne(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String requested = ctx.getArgument("module", String.class);
        FeatureModule module = resolve(requested);
        if (module == null) {
            send(sender, ERROR, RELOAD_UNKNOWN + requested);
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
            send(sender, ERROR, DOCTOR_CLOSED);
            return 0;
        }
        if (!reloadInProgress.compareAndSet(false, true)) {
            send(sender, WARN, RELOAD_RUNNING);
            return 0;
        }
        sendHeader(sender, RELOAD_HEADER);
        sendBody(sender, RELOAD_STARTED);
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
            ReloadStatus status = entry.result().status();
            send(
                    sender,
                    reloadPalette(status),
                    reloadTag(status) + entry.name() + ": " + entry.result().message());
        }
        if (report.hasFailure()) {
            send(sender, ERROR, RELOAD_FAILURE);
        }
        long applied = count(report, ReloadStatus.APPLIED);
        long restart = count(report, ReloadStatus.RESTART_REQUIRED);
        long failed = count(report, ReloadStatus.FAILED);
        String palette = failed > 0 ? ERROR : restart > 0 ? WARN : SUCCESS;
        send(
                sender,
                palette,
                "Summary: %d applied, %d restart-required, %d failed in %d ms."
                        .formatted(applied, restart, failed, elapsedMillis));
        String result = failed > 0 ? "FAILED" : restart > 0 ? "RESTART_REQUIRED" : "SUCCESS";
        send(
                sender,
                palette,
                "Result: " + result + " command=reload applied=" + applied + " restart=" + restart + " failed=" + failed
                        + " elapsedMs=" + elapsedMillis);
    }

    private static long count(ReloadReport report, ReloadStatus status) {
        return report.entries().stream()
                .filter(entry -> entry.result().status() == status)
                .count();
    }

    private static String reloadTag(ReloadStatus status) {
        return switch (status) {
            case APPLIED -> DOCTOR_OK;
            case RESTART_REQUIRED -> RELOAD_RESTART;
            case FAILED -> DOCTOR_FAIL;
        };
    }

    private static String reloadPalette(ReloadStatus status) {
        return switch (status) {
            case APPLIED -> SUCCESS;
            case RESTART_REQUIRED -> WARN;
            case FAILED -> ERROR;
        };
    }

    private void sendModuleLine(CommandSender sender, FeatureModule module) {
        boolean on = module.enabled(config);
        String state = on ? STATE_ENABLED : STATE_DISABLED;
        String colour = on ? SUCCESS : ERROR;
        sender.sendMessage(MINI_MESSAGE.deserialize(BODY + escape(module.id().value()) + colour + escape(state)));
    }

    private static void sendHeader(CommandSender sender, String text) {
        sender.sendMessage(MINI_MESSAGE.deserialize(HEADER + escape(text) + HEADER_END));
    }

    private static void sendBody(CommandSender sender, String text) {
        send(sender, BODY, text);
    }

    /** Render plain operator text under a palette colour, escaping any markup-like characters in it. */
    private static void send(CommandSender sender, String palette, String text) {
        sender.sendMessage(MINI_MESSAGE.deserialize(palette + escape(text)));
    }

    private static String escape(String text) {
        return MINI_MESSAGE.escapeTags(text);
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

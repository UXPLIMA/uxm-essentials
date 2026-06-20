package com.uxplima.uxmessentials.bootstrap.command;

import java.util.List;
import java.util.Objects;

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
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@code /uxmess} operator root (aliases {@code /uxmessentials}, {@code /uxe}).
 *
 * <p>This phase ships working stubs for {@code status}, {@code help}, and {@code reload}; all three
 * read the live {@link ModuleRegistry} so the surface is real rather than a placeholder. Output is
 * deliberately plain, operator-facing diagnostic text — the i18n {@code MessageKey} catalog backs
 * player-facing feature messages and lands with the messaging work. The full {@code reload} that
 * drains and restarts a single module off-tick arrives once modules carry runtime state.
 *
 * <p>{@code doctor} goes deeper than {@code status}: it runs the wired {@link HealthCheck}s — a database
 * liveness probe, the economy-provider ownership check, the soft-depend presence/reachability scan, and the
 * threading/update lines — and prints each as {@code OK}/{@code WARN}/{@code FAIL}. The checks do I/O, so the
 * run is dispatched off the tick thread through the kernel {@link Scheduler} and the rendered lines bridge back
 * to the global region for delivery, exactly as the other off-tick bootstrap commands reply.
 */
@NullMarked
public final class UxmessCommand implements CommandRegistration {

    private static final String ROOT_LITERAL = "uxmess";
    private static final List<String> ALIASES = List.of("uxmessentials", "uxe");
    private static final String DESCRIPTION = "uxmEssentials administration root.";

    private static final String PERMISSION_ADMIN = "uxmessentials.admin";
    private static final String PERMISSION_RELOAD = "uxmessentials.admin.reload";

    private static final String STATUS_HEADER = "uxmEssentials — modules";
    private static final String STATUS_NO_MODULES = "No feature modules are registered yet.";
    private static final String HELP_HEADER = "uxmEssentials commands:";
    private static final String HELP_STATUS = "/uxmess status — list modules and their enable state";
    private static final String HELP_DOCTOR = "/uxmess doctor — run runtime health checks";
    private static final String HELP_HELP = "/uxmess help — show this help";
    private static final String HELP_GUI = "/uxmess gui — open the module management hub";
    private static final String HELP_RELOAD = "/uxmess reload [module] — reload all modules, or one by id";
    private static final String HELP_IMPORT = "/uxmess import <source> [--dry-run] — import legacy data";

    private static final String DOCTOR_HEADER = "uxmEssentials — health checks";
    private static final String DOCTOR_RUNNING = "Running health checks off-tick…";
    private static final String DOCTOR_FAILURE = "One or more checks FAILED — see the lines above.";
    private static final String DOCTOR_OK = "[OK] ";
    private static final String DOCTOR_WARN = "[WARN] ";
    private static final String DOCTOR_FAIL = "[FAIL] ";
    private static final String WARN = "<#ffcc66>";
    private static final String RELOAD_ALL = "Reload requested for all modules.";
    private static final String RELOAD_ONE = "Reload requested for module ";
    private static final String RELOAD_UNKNOWN = "Unknown module: ";
    private static final String STATE_ENABLED = " — enabled";
    private static final String STATE_DISABLED = " — disabled";

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
    private final Scheduler scheduler;
    private final List<HealthCheck> healthChecks;

    public UxmessCommand(
            ModuleRegistry registry,
            ConfigStore config,
            MigrationImportNode importNode,
            GuiSubcommand guiNode,
            Scheduler scheduler,
            List<HealthCheck> healthChecks) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
        this.importNode = Objects.requireNonNull(importNode, "importNode");
        this.guiNode = Objects.requireNonNull(guiNode, "guiNode");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.healthChecks = List.copyOf(Objects.requireNonNull(healthChecks, "healthChecks"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(ROOT_LITERAL)
                .requires(src -> src.getSender().hasPermission(PERMISSION_ADMIN))
                .executes(this::runHelp)
                .then(Commands.literal("status").executes(this::runStatus))
                .then(Commands.literal("doctor").executes(this::runDoctor))
                .then(Commands.literal("help").executes(this::runHelp))
                .then(guiNode.build())
                .then(reloadNode())
                .then(importNode.build())
                .build();
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public List<String> aliases() {
        return ALIASES;
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> reloadNode() {
        return Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission(PERMISSION_RELOAD))
                .executes(this::runReloadAll)
                .then(Commands.argument("module", StringArgumentType.word()).executes(this::runReloadOne));
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
        sendHeader(sender, DOCTOR_HEADER);
        sendBody(sender, DOCTOR_RUNNING);
        // The database probe acquires a connection, so the whole run goes off-tick; the rendered lines bridge
        // back to the global region for delivery, mirroring the other off-tick bootstrap commands.
        scheduler.async(() -> {
            HealthReport report = HealthReport.run(healthChecks);
            scheduler.onGlobal(() -> renderReport(sender, report));
        });
        return Command.SINGLE_SUCCESS;
    }

    private void renderReport(CommandSender sender, HealthReport report) {
        for (HealthReport.Entry entry : report.entries()) {
            sendCheckLine(sender, entry);
        }
        if (report.hasFailure()) {
            send(sender, ERROR, DOCTOR_FAILURE);
        }
    }

    private static void sendCheckLine(CommandSender sender, HealthReport.Entry entry) {
        HealthStatus status = entry.result().status();
        String line = tag(status) + entry.name() + " — " + entry.result().message();
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
        return Command.SINGLE_SUCCESS;
    }

    private int runReloadAll(CommandContext<CommandSourceStack> ctx) {
        sendBody(ctx.getSource().getSender(), RELOAD_ALL);
        return Command.SINGLE_SUCCESS;
    }

    private int runReloadOne(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String requested = ctx.getArgument("module", String.class);
        FeatureModule module = resolve(requested);
        if (module == null) {
            send(sender, ERROR, RELOAD_UNKNOWN + requested);
            return 0;
        }
        send(sender, SUCCESS, RELOAD_ONE + module.id().value());
        return Command.SINGLE_SUCCESS;
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
}

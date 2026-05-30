package com.uxplima.uxmessentials.bootstrap.command;

import java.util.List;
import java.util.Objects;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
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
    private static final String HELP_HELP = "/uxmess help — show this help";
    private static final String HELP_RELOAD = "/uxmess reload [module] — reload all modules, or one by id";
    private static final String RELOAD_ALL = "Reload requested for all modules.";
    private static final String RELOAD_ONE = "Reload requested for module ";
    private static final String RELOAD_UNKNOWN = "Unknown module: ";
    private static final String STATE_ENABLED = " — enabled";
    private static final String STATE_DISABLED = " — disabled";

    private final ModuleRegistry registry;
    private final ConfigStore config;

    public UxmessCommand(ModuleRegistry registry, ConfigStore config) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(ROOT_LITERAL)
                .requires(src -> src.getSender().hasPermission(PERMISSION_ADMIN))
                .executes(this::runHelp)
                .then(Commands.literal("status").executes(this::runStatus))
                .then(Commands.literal("help").executes(this::runHelp))
                .then(reloadNode())
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
        sender.sendMessage(Component.text(STATUS_HEADER));
        List<FeatureModule> modules = registry.all();
        if (modules.isEmpty()) {
            sender.sendMessage(Component.text(STATUS_NO_MODULES));
            return Command.SINGLE_SUCCESS;
        }
        for (FeatureModule module : modules) {
            String state = module.enabled(config) ? STATE_ENABLED : STATE_DISABLED;
            sender.sendMessage(Component.text(module.id().value() + state));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(Component.text(HELP_HEADER));
        sender.sendMessage(Component.text(HELP_STATUS));
        sender.sendMessage(Component.text(HELP_HELP));
        sender.sendMessage(Component.text(HELP_RELOAD));
        return Command.SINGLE_SUCCESS;
    }

    private int runReloadAll(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(Component.text(RELOAD_ALL));
        return Command.SINGLE_SUCCESS;
    }

    private int runReloadOne(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String requested = ctx.getArgument("module", String.class);
        FeatureModule module = resolve(requested);
        if (module == null) {
            sender.sendMessage(Component.text(RELOAD_UNKNOWN + requested));
            return 0;
        }
        sender.sendMessage(Component.text(RELOAD_ONE + module.id().value()));
        return Command.SINGLE_SUCCESS;
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

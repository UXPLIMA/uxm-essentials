package com.uxplima.uxmessentials.customcommands.adapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.customcommands.adapter.inbound.command.CustomCommandCommand;
import com.uxplima.uxmessentials.customcommands.adapter.inbound.command.CustomCommandRegistration;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.CurrenciesCommandFee;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.MenuActionRunner;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.MenuRequirementCheck;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.MessageRunFeedback;
import com.uxplima.uxmessentials.customcommands.application.CustomCommandsConfig;
import com.uxplima.uxmessentials.customcommands.application.RunCustomCommand;
import com.uxplima.uxmessentials.customcommands.application.port.CommandFee;
import com.uxplima.uxmessentials.customcommands.domain.ChainDepth;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the customcommands context: one load pass over {@code commands/custom/*.conf}, the four ports the use
 * case drives, one Brigadier registration per loaded definition, and the {@code /customcmd} operator surface. This
 * is the one place the context is wired; nothing else news up its classes.
 *
 * <p>The load result is held in an {@link AtomicReference} that {@code /customcmd list}, {@code info}, {@code run}
 * and {@code test} read and a reload swaps in one assignment, so a reader sees either the old catalog or the new
 * one, never a half-applied pass.
 *
 * <p>Brigadier registers only while the server starts, exactly as a menu's own {@code command {}} block does. A
 * reload therefore refreshes a definition's body, its gates, its requirements and its actions, but cannot add,
 * rename or drop a command word: that waits for the next restart. {@code /customcmd run <id>} covers a freshly
 * written definition in the meantime, so nothing is untestable until then.
 */
@NullMarked
public final class CustomCommandsWiring {

    /** Where an operator's definitions live, relative to the plugin data folder. */
    private static final String DEFINITIONS_DIRECTORY = "commands/custom";

    private CustomCommandsWiring() {}

    /** Everything the bootstrap installs for this context. */
    public record Wired(List<CommandRegistration> commands, List<Listener> listeners) {

        public Wired {
            commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
            listeners = List.copyOf(Objects.requireNonNull(listeners, "listeners"));
        }
    }

    /** Read the definitions, build the ports and hand back the commands and listeners to install. */
    public static Wired wire(ModuleContext ctx, Menus menus, Currencies currencies, Path dataFolder) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(dataFolder, "dataFolder");
        CustomCommandsConfig config = CustomCommandsConfig.from(ctx.config());
        Path directory = dataFolder.resolve(DEFINITIONS_DIRECTORY);
        CustomCommandLoader loader = new CustomCommandLoader(ctx.kernel().log());
        CustomCommandLoader.LoadResult initial = loader.loadFrom(directory, config.chainLimits());
        AtomicReference<CustomCommandLoader.LoadResult> state = new AtomicReference<>(initial);

        CommandFee fee = new CurrenciesCommandFee(currencies, config::currency);
        MenuActionRunner actions = new MenuActionRunner(
                menus,
                ctx.kernel().scheduler(),
                ctx.kernel().log(),
                () -> new MenuActionRunner.PrivilegedActions(
                        config.allowConsoleActions(), config.allowOpActions(), config.logPrivilegedActions()));
        RunCustomCommand runner = new RunCustomCommand(
                ctx.kernel().permissions(),
                ctx.kernel().cooldowns(),
                ctx.kernel().warmups(),
                actions,
                new MenuRequirementCheck(menus),
                fee,
                new MessageRunFeedback(ctx.kernel().messages(), ctx.kernel().messageSink(), fee),
                new ChainDepth(config.maxChainDepth()),
                ctx.kernel().log());

        List<CommandRegistration> commands = new ArrayList<>();
        for (CustomCommand command : initial.catalog().commands()) {
            String id = command.id().value();
            commands.add(new CustomCommandRegistration(
                    command,
                    initial.argumentSpecs().getOrDefault(id, List.of()),
                    runner,
                    () -> Objects.requireNonNull(state.get(), "state")
                            .catalog()
                            .byId(id)
                            .orElse(command)));
        }
        commands.add(new CustomCommandCommand(
                state,
                () -> loader.loadFrom(directory, config.chainLimits()),
                id -> loader.loadOne(directory.resolve(id + ".conf"), config.chainLimits()),
                runner,
                ctx.kernel().scheduler(),
                ctx.kernel().messages()));
        return new Wired(commands, List.of());
    }
}

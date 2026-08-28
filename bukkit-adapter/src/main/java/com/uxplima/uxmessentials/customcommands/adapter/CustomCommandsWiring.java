package com.uxplima.uxmessentials.customcommands.adapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.customcommands.adapter.inbound.command.CreateWizard;
import com.uxplima.uxmessentials.customcommands.adapter.inbound.command.CustomCommandCommand;
import com.uxplima.uxmessentials.customcommands.adapter.inbound.command.CustomCommandRegistration;
import com.uxplima.uxmessentials.customcommands.adapter.inbound.command.WizardPrompt;
import com.uxplima.uxmessentials.customcommands.adapter.inbound.listener.CommandWarmupTracker;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.CurrenciesCommandFee;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.MenuActionRunner;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.MenuRequirementCheck;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.MessageRunFeedback;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.TrackingCommandWarmups;
import com.uxplima.uxmessentials.customcommands.application.CustomCommandsConfig;
import com.uxplima.uxmessentials.customcommands.application.RunCustomCommand;
import com.uxplima.uxmessentials.customcommands.application.port.CommandFee;
import com.uxplima.uxmessentials.customcommands.domain.ChainDepth;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
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
    public static Wired wire(
            ModuleContext ctx, Menus menus, Currencies currencies, Path dataFolder, WizardPrompt prompt) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(prompt, "prompt");
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
        // The warmup a definition declares is cancelled by movement, the same rule teleports live under. The tracker
        // is this context's own, because a command warmup has one axis to reconcile and no toggles to consult.
        CommandWarmupTracker warmups = new CommandWarmupTracker();
        RunCustomCommand runner = new RunCustomCommand(
                ctx.kernel().permissions(),
                ctx.kernel().cooldowns(),
                new TrackingCommandWarmups(ctx.kernel().warmups(), warmups),
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
        CreateWizard wizard = new CreateWizard(
                prompt,
                directory,
                () -> Set.copyOf(
                        Objects.requireNonNull(state.get(), "state").catalog().ids()),
                new CommandFeedback(ctx.kernel().messages()),
                // A saved definition is loaded straight away, so /customcmd run and /customcmd info answer for it
                // before the restart that makes its own word typeable.
                id -> state.set(loader.loadFrom(directory, config.chainLimits())),
                ctx.kernel().log());
        commands.add(new CustomCommandCommand(
                state,
                () -> loader.loadFrom(directory, config.chainLimits()),
                id -> loader.loadOne(directory.resolve(id + ".conf"), config.chainLimits()),
                runner,
                ctx.kernel().scheduler(),
                ctx.kernel().messages(),
                wizard,
                id -> delete(directory, id, state, loader, config)));
        return new Wired(commands, List.of(warmups));
    }

    /**
     * Remove one definition's file and re-read the folder. Returns the empty string on success, or a reason: the
     * caller turns that into the operator's message rather than this method reaching for the catalogue.
     */
    private static String delete(
            Path directory,
            String id,
            AtomicReference<CustomCommandLoader.LoadResult> state,
            CustomCommandLoader loader,
            CustomCommandsConfig config) {
        try {
            if (!Files.deleteIfExists(directory.resolve(id + ".conf"))) {
                return "no file named '" + id + ".conf'";
            }
        } catch (IOException failure) {
            return String.valueOf(failure.getMessage());
        }
        state.set(loader.loadFrom(directory, config.chainLimits()));
        return "";
    }
}

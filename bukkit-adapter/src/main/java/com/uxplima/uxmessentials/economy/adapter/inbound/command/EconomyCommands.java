package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the economy context's Brigadier command surface (docs/10-feature-modules.md §15.4) as
 * {@link CommandRegistration}s over the constructed {@link EconomyServices}. Collected in one greppable table
 * so the literal/permission pairing matches {@code permissions.md} §Economy and the kernel's
 * {@code EconomyCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS} handler registers each. Every
 * command is a thin adapter over the {@code EconomyProvider} port and runs its provider call off the tick
 * thread — none reaches into {@code economy.domain.*}.
 */
@NullMarked
public final class EconomyCommands {

    private EconomyCommands() {}

    /** Every economy command, in surface order. */
    public static List<CommandRegistration> all(EconomyServices services, Messages messages) {
        return List.of(
                new BalanceCommand(services, messages),
                new PayCommand(services, messages),
                new PayConfirmCommand(services, messages),
                new PayAllCommand(services, messages),
                new PayToggleCommand(services, messages),
                new BaltopCommand(services, messages),
                new WorthCommand(services, messages),
                new SellCommand(services, messages),
                new SellAllCommand(services, messages),
                new EcoCommand(services, messages));
    }
}

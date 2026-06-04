package com.uxplima.uxmessentials.kits.adapter.inbound.command;

import java.util.List;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the kits context's Brigadier command surface (docs/10-feature-modules.md §15.5) as
 * {@link CommandRegistration}s over the constructed {@link KitServices}. Collected in one greppable table so
 * the literal/permission pairing matches the permissions reference and the kernel's {@code KitCommandSurface};
 * the plugin's {@code LifecycleEvents.COMMANDS} handler registers each.
 */
@NullMarked
public final class KitCommands {

    private KitCommands() {}

    /** Every kits command, in surface order. */
    public static List<CommandRegistration> all(
            KitServices services, Messages messages, Supplier<ListDisplayMode> displayMode) {
        return List.of(
                new KitCommand(services, messages),
                new KitsCommand(services, messages, displayMode),
                new ShowKitCommand(services, messages),
                new CreateKitCommand(services, messages),
                new DelKitCommand(services, messages),
                new KitEditorCommand(services, messages),
                new KitResetCommand(services, messages));
    }
}

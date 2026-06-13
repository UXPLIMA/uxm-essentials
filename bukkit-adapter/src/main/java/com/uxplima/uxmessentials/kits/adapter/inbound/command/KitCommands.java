package com.uxplima.uxmessentials.kits.adapter.inbound.command;

import java.util.List;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the kits context's Brigadier command surface (docs/10-feature-modules.md §15.5) as a single
 * {@link CommandRegistration} over the constructed {@link KitServices}. Everything a player or operator does
 * with kits hangs off one {@code /kit} command: {@code <name>} claims a kit, while {@code list}, {@code show},
 * {@code create}, {@code del}, {@code editor} and {@code reset} are Brigadier literals each gated by its own
 * permission node via {@code .requires(...)}. Collected here so the literal/permission pairing matches the
 * permissions reference and the kernel's {@code KitCommandSurface}; the plugin's
 * {@code LifecycleEvents.COMMANDS} handler registers it. Mirrors the subcommand-tree idiom {@code /warp} and
 * {@code /home} use.
 */
@NullMarked
public final class KitCommands {

    private KitCommands() {}

    /**
     * The single kits command. {@code listDisplay} selects how {@code /kit list} presents its entries (the
     * browse menu or the chat list); {@code previewDisplay} selects how {@code /kit show} presents a kit (the
     * read-only GUI or the chat contents). Both are read live so a module reload takes effect.
     */
    public static List<CommandRegistration> all(
            KitServices services,
            Messages messages,
            Supplier<ListDisplayMode> listDisplay,
            Supplier<ListDisplayMode> previewDisplay) {
        return List.of(new KitCommand(services, messages, listDisplay, previewDisplay));
    }
}

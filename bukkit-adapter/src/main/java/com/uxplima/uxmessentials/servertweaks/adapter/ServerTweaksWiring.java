package com.uxplima.uxmessentials.servertweaks.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.ServerBrandJoinListener;
import com.uxplima.uxmessentials.servertweaks.adapter.outbound.ConsoleFilterInstaller;
import com.uxplima.uxmessentials.servertweaks.adapter.outbound.ConsoleSpamFilter;
import com.uxplima.uxmessentials.servertweaks.adapter.outbound.PluginMessageBrandSender;
import com.uxplima.uxmessentials.servertweaks.application.ServerTweaksConfig;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the server-tweaks context's Bukkit-facing effects over the module's scoped config, wiring each tweak
 * only when its own switch is on. The module ships enabled but every tweak defaults off, so a fresh install wires
 * nothing here until an operator opts a tweak in.
 *
 * <ul>
 *   <li><b>customf3brand</b> — when {@code f3-brand.enabled}, the {@code minecraft:brand} outgoing channel is
 *       registered and a {@link ServerBrandJoinListener} re-sends the configured brand to each joiner so it shows on
 *       F3; the channel is unregistered on stop.
 *   <li><b>console-spam-fix</b> — when {@code console-filter.enabled}, a {@link ConsoleSpamFilter} is attached to the
 *       root Log4j2 logger through the {@link ConsoleFilterInstaller} and detached again on stop.
 * </ul>
 *
 * <p>{@link Wired#stop()} unwinds every effect (unregister the channel, detach the filter) so a disable or reload
 * leaves the server exactly as it was found.
 */
@NullMarked
public final class ServerTweaksWiring {

    private ServerTweaksWiring() {}

    /** Build the enabled tweaks' listeners and their unwind hooks from {@code plugin} and {@code ctx}. */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        ServerTweaksConfig config = ServerTweaksConfig.from(ctx.config());
        List<Listener> listeners = new ArrayList<>();
        List<Runnable> stops = new ArrayList<>();
        wireF3Brand(plugin, config.f3Brand(), listeners, stops);
        wireConsoleFilter(config.consoleFilter(), ctx.kernel().log(), stops);
        return new Wired(List.copyOf(listeners), List.copyOf(stops));
    }

    private static void wireF3Brand(
            Plugin plugin, ServerTweaksConfig.F3Brand f3Brand, List<Listener> listeners, List<Runnable> stops) {
        if (!f3Brand.enabled()) {
            return;
        }
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PluginMessageBrandSender.BRAND_CHANNEL);
        PluginMessageBrandSender sender = new PluginMessageBrandSender(plugin, f3Brand.brand());
        listeners.add(new ServerBrandJoinListener(true, sender));
        stops.add(() -> plugin.getServer()
                .getMessenger()
                .unregisterOutgoingPluginChannel(plugin, PluginMessageBrandSender.BRAND_CHANNEL));
    }

    private static void wireConsoleFilter(
            ServerTweaksConfig.ConsoleFilter consoleFilter, Logger log, List<Runnable> stops) {
        if (!consoleFilter.enabled()) {
            return;
        }
        ConsoleFilterInstaller installer =
                new ConsoleFilterInstaller(new ConsoleSpamFilter(consoleFilter.toPolicy()), log);
        installer.install();
        stops.add(installer::uninstall);
    }

    /**
     * Everything the server-tweaks module contributes once wired: the listeners the plugin registers and the unwind
     * hooks the plugin runs on stop (unregister the brand channel, detach the console filter).
     *
     * @param listeners the Bukkit listeners to register (the F3-brand join listener, when that tweak is on)
     * @param stops the per-tweak unwind hooks, run in reverse on module stop
     */
    public record Wired(List<Listener> listeners, List<Runnable> stops) {

        public Wired {
            listeners = List.copyOf(listeners);
            stops = List.copyOf(stops);
        }

        /** Unwind every enabled tweak's effect, so a disable or reload strands no channel and no log filter. */
        public void stop() {
            for (int i = stops.size() - 1; i >= 0; i--) {
                stops.get(i).run();
            }
        }
    }
}

package com.uxplima.uxmessentials.itemworld.adapter;

import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.itemworld.adapter.inbound.command.ItemworldGroupACommands;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.command.ItemworldGroupBCommands;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.command.ItemworldGuiCommand;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.ItemworldHubView;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.listener.PowertoolInteractListener;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.listener.UnlimitedPlacementListener;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.LoggingItemworldAudit;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.PdcPowertoolStore;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.PowertoolToggleStore;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.UnlimitedPlacementStore;
import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.application.PowertoolPolicy;
import com.uxplima.uxmessentials.itemworld.application.PurgePolicy;
import com.uxplima.uxmessentials.itemworld.application.port.ItemworldAudit;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.slf4j.LoggerFactory;

/**
 * Constructs the itemworld context's adapters over the injected kernel ports and produces everything the plugin
 * must register: the full Brigadier command list (groups A and B — the ~40-verb surface) and the powertool +
 * unlimited-placement listeners. This is the one place the itemworld context is wired — nothing else news up
 * its classes (the wiring invariant, docs/10-feature-modules.md §5).
 *
 * <p>itemworld is stateless and ACL-thin: it needs no database, only the {@link KernelPorts} the commands
 * schedule and render through, the {@link ItemworldConfig} view of {@code itemworld.conf} (the sub-feature-group
 * + per-command disable gate, the {@code /give}//{@code /more} caps, the enchant-level clamp, the audit
 * toggles), and the {@link ItemworldAudit} on the dedicated {@code com.uxplima.uxmessentials.audit} channel
 * (mirroring {@code LoggingModerationAudit}). The two stateful corners — powertool bindings (item PDC) and the
 * powertool / unlimited per-player toggles — are transient runtime state dropped with the wiring on module stop;
 * the {@code Plugin} handle is used only to mint the powertool PDC {@code NamespacedKey} once.
 */
@NullMarked
public final class ItemworldWiring {

    private static final String AUDIT_CHANNEL = "com.uxplima.uxmessentials.audit";
    private static final int DISPOSAL_ROWS = 6;

    private ItemworldWiring() {}

    /** Build the itemworld adapters from {@code ctx}, ready to register with the plugin. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, GuiLayouts guiLayouts) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        KernelPorts kernel = ctx.kernel();
        ItemworldConfig config = ItemworldConfig.from(ctx.config());
        ItemworldAudit audit = new LoggingItemworldAudit(auditLogger(), config);
        GuiLayout disposalLayout = guiLayouts.load("itemworld", "disposal", GuiLayout.storageDefault(DISPOSAL_ROWS));
        ItemworldServices services = new ItemworldServices(kernel, audit, config, disposalLayout);

        PowertoolPolicy powertoolPolicy = new PowertoolPolicy();
        PurgePolicy purgePolicy = new PurgePolicy(config);
        PdcPowertoolStore powertoolStore = new PdcPowertoolStore(plugin);
        PowertoolToggleStore powertoolToggles = new PowertoolToggleStore();
        UnlimitedPlacementStore unlimited = new UnlimitedPlacementStore();

        // The utilities hub reuses the SP0 GUI framework over the shared catalog and the data-folder layout loader.
        // Each launcher button runs the same surface a command does (open a workstation, set time/weather, sweep
        // drops/mobs) and is drawn only when the viewer holds that command's permission. /itemworld gui and the
        // /uxmess gui hub both open it; no new domain logic — only the surfaces the commands expose.
        GuiText guiText = new GuiText(kernel.messages());
        ItemworldHubView hubView = new ItemworldHubView(
                guiText, kernel.scheduler(), kernel.permissions(), services, purgePolicy, guiLayouts);

        List<CommandRegistration> commands = new java.util.ArrayList<>(ItemworldGroupACommands.all(services));
        commands.addAll(ItemworldGroupBCommands.all(
                services, powertoolPolicy, purgePolicy, powertoolStore, powertoolToggles, unlimited));
        commands.add(new ItemworldGuiCommand(hubView, kernel.messages(), kernel.messageSink()));
        List<Listener> listeners = List.of(
                new PowertoolInteractListener(powertoolStore, powertoolToggles, config),
                new UnlimitedPlacementListener(unlimited, config));
        return new Wired(List.copyOf(commands), listeners, hubView);
    }

    private static Logger auditLogger() {
        return new Slf4jLogger(LoggerFactory.getLogger(AUDIT_CHANNEL));
    }

    /**
     * Everything the itemworld module contributes once wired: the full Brigadier command list and the powertool
     * + unlimited-placement listeners. The context holds no repeating scheduled work and no persistence; its
     * only runtime state is the powertool/unlimited toggle maps and the item-PDC bindings, which are
     * garbage-collected with the wiring on module stop — there is nothing to drain or flush.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the powertool interact + unlimited-placement listeners to register
     * @param hubView the utilities hub the {@code /uxmess gui} hub entry opens
     */
    public record Wired(List<CommandRegistration> commands, List<Listener> listeners, ItemworldHubView hubView) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(hubView, "hubView");
        }
    }
}

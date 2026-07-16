package com.uxplima.uxmessentials.survival.adapter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.survival.adapter.inbound.command.TreeFellerCommand;
import com.uxplima.uxmessentials.survival.adapter.inbound.command.VeinminerCommand;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.TreeFellerListener;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.VeinminerListener;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the survival context's adapters over the injected kernel ports and produces the mechanic commands and
 * {@code BlockBreakEvent} listeners the plugin registers. Each mechanic wires only when its config gate is on: with
 * {@code tree-feller.enabled = false} no {@code /treefeller} command and no tree-feller listener land, and likewise
 * for veinminer — so a disabled mechanic contributes nothing, the same "disabled means absent" property the module
 * gate gives at the context level.
 *
 * <p>The context persists nothing — the per-player toggle is a transient PDC stamp held in
 * {@link PdcSurvivalToggles} — so there is no repository, migration, or teardown state; the {@link Wired} record is
 * just the commands and listeners to publish.
 */
@NullMarked
public final class SurvivalWiring {

    private SurvivalWiring() {}

    /** Build the survival commands and listeners from {@code ctx}, ready to register. */
    public static Wired wire(ModuleContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        SurvivalConfig config = SurvivalConfig.from(ctx.config());
        PdcSurvivalToggles toggles = new PdcSurvivalToggles();

        List<CommandRegistration> commands = new ArrayList<>();
        List<Listener> listeners = new ArrayList<>();
        if (config.treeFeller().enabled()) {
            commands.add(new TreeFellerCommand(toggles, kernel.messages()));
            listeners.add(new TreeFellerListener(config.treeFeller(), toggles, kernel.scheduler()));
        }
        if (config.veinminer().enabled()) {
            commands.add(new VeinminerCommand(toggles, kernel.messages()));
            Set<Material> triggers = triggerMaterials(config.veinminer(), kernel.log());
            listeners.add(new VeinminerListener(config.veinminer(), triggers, toggles));
        }
        return new Wired(commands, listeners);
    }

    /** Resolve the configured veinminer trigger names to materials, warning on and skipping any unknown id. */
    private static Set<Material> triggerMaterials(SurvivalConfig.Veinminer config, Logger log) {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : config.blocks()) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                log.warn("survival veinminer: unknown block material '{}' — skipping", name);
            } else {
                materials.add(material);
            }
        }
        return materials;
    }

    /**
     * Everything the survival module contributes once wired: the mechanic toggle commands and the break listeners,
     * each present only when its mechanic is enabled. The context holds no runtime state, so there is nothing to
     * drain on stop.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     */
    public record Wired(List<CommandRegistration> commands, List<Listener> listeners) {
        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
        }
    }
}

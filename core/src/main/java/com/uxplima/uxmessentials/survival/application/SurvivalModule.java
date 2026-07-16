package com.uxplima.uxmessentials.survival.application;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The survival bounded context as a first-class {@link FeatureModule}: opt-in gameplay mechanics, each an
 * independently toggleable sub-feature. Phase 1 ships the harvesting core — tree-feller (fell a whole tree from one
 * log) and veinminer (break a whole ore vein) — with per-player {@code /treefeller} and {@code /veinminer} toggles.
 * The commands, the {@code BlockBreakEvent} listeners, and the PDC toggle store are Bukkit-facing and land with the
 * adapter wiring ({@code SurvivalWiring}); this module stands up the identity, the enable gate, and the lifecycle
 * bookkeeping so the mechanics wire behind it.
 *
 * <p><b>Ships enabled by default.</b> The bundled config turns both mechanics on out of the box, so
 * {@link #enabled(ConfigStore)} defaults to {@code true} like the steady-state contexts; an operator flips
 * {@code modules.survival.enabled = false} to turn the whole feature off, or a per-mechanic {@code enabled = false}
 * to disable one. It persists nothing — the per-player toggle is a transient PDC stamp — so the module owns no Flyway
 * location.
 *
 * <p>Like the poses context, the mechanic commands and listeners are contributed through the adapter wiring rather
 * than the declarative {@link #commands()} / {@link #listeners()} lists, so those return empty here; a disabled
 * module wires zero either way (the {@code FeatureModule} contract).
 */
@NullMarked
public final class SurvivalModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("survival");

    private volatile boolean running;

    @Override
    public ModuleId id() {
        return ID;
    }

    @Override
    public String configRoot() {
        return ID.configRoot();
    }

    @Override
    public List<CommandSpec> commands() {
        // The /treefeller and /veinminer toggles are Bukkit-facing and land through the inbound adapter wiring
        // (SurvivalWiring), conditionally on each mechanic's config gate; the declarative list stays empty.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The BlockBreakEvent listeners land through the inbound adapter wiring; a disabled or not-yet-adapted
        // module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // survival persists nothing: the per-player toggle is a transient PDC stamp, so the module owns no Flyway
        // location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships enabled: the bundled config turns tree-feller and veinminer on out of the box. The
        // default here is true (the steady-state contexts default on); an operator opts out via modules.conf.
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The typed config and the mechanic adapters are constructed over ctx in the adapter wiring; the lifecycle
        // bookkeeping (running flag) is armed here so stop() is already honest before any behaviour lands.
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** True while the module is started; exposed for parity with the other contexts' lifecycle bookkeeping. */
    public boolean isRunning() {
        return running;
    }
}

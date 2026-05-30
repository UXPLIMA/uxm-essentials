package com.uxplima.uxmessentials.warps.application;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The warps bounded context as a first-class {@link FeatureModule}: it owns the warp aggregate, its
 * access gates (per-warp permission plus an optional cost), and the warp CRUD/list/info commands, and it
 * <em>delegates</em> teleport execution to the teleport context (so it is registered after teleport in
 * {@code DefaultModuleRegistry}). The per-warp cost soft-couples to the economy context: warps charge only
 * when an economy provider is present, so the module is registered after economy as well but never hard
 * depends on it. The module declares its command surface and enable gate here; {@code start} arms the
 * lifecycle bookkeeping, and the bukkit-side adapters (the Brigadier handlers and the jOOQ repository over
 * {@code persistence.dsl()}) are constructed in the adapter wiring once the module has started.
 *
 * <p>The warps table ships in the persistence V1 baseline ({@code db/migration}), which the persistence
 * layer always applies, so the module declares no extra migration location of its own; a disabled module
 * still leaves the baseline table in place but wires nothing over it.
 */
@NullMarked
public final class WarpsModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("warps");
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private final AtomicInteger inFlight = new AtomicInteger();
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
        return WarpCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The warps context registers no Bukkit listener; a disabled or not-yet-adapted module registers
        // none either.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The warps table is part of the persistence V1 baseline (db/migration), always applied by the
        // persistence layer, so the module owns no additional Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The use cases and the jOOQ repository are constructed over ctx.kernel() and the persistence DSL
        // in the adapter wiring; the lifecycle bookkeeping is armed here so stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; async warp loads observe this and exit on stop. */
    public boolean isRunning() {
        return running;
    }

    private void awaitDrain() {
        long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
        while (inFlight.get() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }
}

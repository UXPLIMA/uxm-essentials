package com.uxplima.uxmessentials.playerwarps.application;

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
 * The player-warps bounded context as a first-class {@link FeatureModule}: it owns the per-owner
 * {@code PlayerWarp} aggregate, its ownership/public-flag access gates, the per-owner count limit, and the
 * {@code /setpwarp} {@code /delpwarp} {@code /pwarp} {@code /pwarps} commands, and it <em>delegates</em>
 * teleport execution to the teleport context (so it is registered after teleport in
 * {@code DefaultModuleRegistry}). The module declares its command surface and enable gate here; {@code start}
 * arms the lifecycle bookkeeping, and the bukkit-side adapters (the Brigadier handlers and the jOOQ
 * repository over {@code persistence.dsl()}) are constructed in the adapter wiring once the module has
 * started.
 *
 * <p>The {@code player_warps} table ships in the persistence V14 baseline ({@code db/migration}), which the
 * persistence layer always applies, so the module declares no extra migration location of its own; a disabled
 * module still leaves the baseline table in place but wires nothing over it.
 */
@NullMarked
public final class PlayerwarpsModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("playerwarps");
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
        return PlayerWarpCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The player-warps context registers no Bukkit listener; a disabled or not-yet-adapted module
        // registers none either.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The player_warps table is part of the persistence V14 baseline (db/migration), always applied by the
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
        // The use cases and the jOOQ repository are constructed over ctx.kernel() and the persistence DSL in
        // the adapter wiring; the lifecycle bookkeeping is armed here so stop() is already honest.
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

package com.uxplima.uxmessentials.homes.application;

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
 * The homes bounded context as a first-class {@link FeatureModule}: it owns the home aggregate, its
 * numbered-quota resolution, and the home CRUD/list/admin commands, and it <em>delegates</em> teleport
 * execution to the teleport context (so it models a soft cross-context dependency, which is why it is
 * registered after teleport in {@code DefaultModuleRegistry}). The module declares its command surface
 * and enable gate here; {@code start} arms the lifecycle bookkeeping, and the bukkit-side adapters (the
 * Brigadier handlers and the jOOQ repository over {@code persistence.dsl()}) are constructed in the
 * adapter wiring once the module has started.
 *
 * <p>The homes and warps tables ship in the persistence V1 baseline ({@code db/migration}), which the
 * persistence layer always applies, so the module declares no extra migration location of its own; a
 * disabled module still leaves the baseline tables in place but wires nothing over them.
 */
@NullMarked
public final class HomesModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("homes");
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
        return HomeCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // A join-time cache warm-up listener is Bukkit-facing and lands with the inbound adapter; a
        // disabled or not-yet-adapted module registers none.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The homes table is part of the persistence V1 baseline (db/migration), always applied by the
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
        // The use cases and the jOOQ repository are constructed over ctx.kernel() and the persistence
        // DSL in the adapter wiring; the lifecycle bookkeeping (running flag, in-flight counter) is armed
        // here so stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; async home loads observe this and exit on stop. */
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

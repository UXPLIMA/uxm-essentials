package com.uxplima.uxmessentials.teleport.application;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The teleport bounded context as a first-class {@link FeatureModule}: it owns all movement
 * orchestration and the shared cooldown/warmup machinery, the tpa suite, {@code /back}, {@code /rtp},
 * spawn, the admin and positional verbs, plus the move-cancels-warmup invariant. The module declares
 * its command surface and its enable gate here; {@code start} constructs the use cases over the injected
 * kernel ports and the context's own ports, and {@code stop} drains them so a disabled module holds zero
 * runtime state.
 *
 * <p>This module holds the pure {@code :core} domain and application. The inbound adapters (the
 * Brigadier handlers, the move/death/expiry listeners) and the outbound adapters (the back store, the
 * pre-warmed RTP queue, the teleport executor) live in the bukkit-adapter and persistence-adapter, wired
 * by the adapter wiring, so {@link #listeners()} and {@link #migrations()} are empty, and
 * {@link #start(ModuleContext)} only arms the lifecycle bookkeeping.
 */
@NullMarked
public final class TeleportModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("teleport");
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
    public List<ListenerFactory> listeners() {
        // The move-cancel, death-capture, request-expiry, and join re-arm listeners are Bukkit-facing
        // and come from the inbound adapter; a disabled module registers none.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The pre-warmed RTP queue's durable mirror (the rtp_pool table) ships in the persistence
        // baseline, so the module owns no migrations of its own.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The use cases are constructed over ctx.kernel() and the context's own ports in the adapter
        // wiring. The lifecycle bookkeeping (running flag, in-flight counter) is armed here so stop() is
        // honest even if the wiring fails part way.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; self-rescheduling work observes this and exits on stop. */
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

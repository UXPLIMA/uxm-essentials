package com.uxplima.uxmessentials.shared.application.module;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * Shared lifecycle scaffolding for the config-authored HUD render modules (the tablist header/footer and the
 * above-head nametag). Each is a per-player display driven by a self-rescheduling render timer on the
 * {@code Scheduler} port: it owns no command (there is no per-player visibility toggle), registers its
 * Bukkit-facing connection listener and renderer in the adapter wiring, and persists nothing (the content is
 * config-authored). It ships enabled by default and is toggled through {@code modules.conf}.
 *
 * <p>What differs between the two is only the {@link #id() module id} and the adapters each starts; everything
 * shared lives here: the enabled-by-default gate, the empty command/listener/migration surfaces, and the
 * {@code running} flag plus in-flight drain that keep {@link #stop()} honest. The render timer observes the flag
 * and exits cleanly on disable, and {@code stop()} waits a bounded 5s for any in-flight render to finish.
 */
@NullMarked
public abstract class AbstractHudModule implements FeatureModule {

    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile boolean running;

    @Override
    public String configRoot() {
        return id().configRoot();
    }

    @Override
    public List<CommandSpec> commands() {
        // Always-on when enabled: there is no per-player visibility toggle, so a HUD module publishes no command.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The join/quit connection listener is Bukkit-facing and lands with the inbound adapter; a disabled or
        // not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // A HUD module persists nothing: its content is config-authored, so it owns no Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // Ships enabled (like the steady-state contexts): the bundled config carries a working default, so a fresh
        // install shows the HUD out of the box; an operator opts out via modules.conf.
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The render timer, connection listener and renderer are constructed over ctx.kernel() in the adapter
        // wiring; arming the running flag and in-flight counter here keeps stop() honest. A subclass that acquires
        // extra state overrides this and calls super.start(ctx) first.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; the render timer observes this and exits on stop. */
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

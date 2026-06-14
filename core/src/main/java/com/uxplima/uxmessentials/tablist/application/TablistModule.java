package com.uxplima.uxmessentials.tablist.application;

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
 * The tablist bounded context as a first-class {@link FeatureModule}: a per-player tablist header and footer rendered
 * from operator-authored MiniMessage content under {@code modules/tablist/config.conf} through the placeholder
 * pipeline. It owns the self-rescheduling render timer on the {@code Scheduler} port that re-renders every viewer each
 * configured refresh interval; the render/join/quit machinery is Bukkit-facing and lands with the adapter wiring.
 *
 * <p><b>Ships enabled by default.</b> A fresh install bundles an example header/footer authored with the built-in
 * {@code {online}}/{@code {max_players}} tokens (no PlaceholderAPI required), so out of the box a new operator sees a
 * working tab and brands or disables it from there. The
 * {@link #enabled(ConfigStore)} gate therefore defaults to {@code true} (like the
 * steady-state contexts). It persists nothing: the tablist is entirely config-authored, so the module
 * owns no Flyway location.
 *
 * <p>The tablist is always-on for every viewer when enabled — there is no per-player visibility toggle and so the
 * module publishes no command. The render timer, the connection listener, and the renderer are constructed in the
 * adapter wiring once the module has started; the lifecycle bookkeeping here keeps {@code stop()} honest — the render
 * timer observes the running flag and exits cleanly on disable.
 */
@NullMarked
public final class TablistModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("tablist");
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
        // The tablist is always-on when enabled — there is no per-player visibility toggle, so the module publishes
        // no command. The header/footer are rendered by the adapter's render timer.
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
        // tablist persists nothing: the header/footer content is config-authored, so the module owns no Flyway
        // location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships ENABLED: a fresh install bundles an example header/footer (built-in {tokens}, no
        // PlaceholderAPI required), so a new operator sees a working tab out of the box and opts out via modules.conf
        // if unwanted.
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The render timer on the Scheduler port, the connection listener, and the renderer over uxmLib's Tablist are
        // constructed over ctx.kernel() in the adapter wiring; the lifecycle bookkeeping (running flag, in-flight
        // counter) is armed here so stop() is already honest.
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

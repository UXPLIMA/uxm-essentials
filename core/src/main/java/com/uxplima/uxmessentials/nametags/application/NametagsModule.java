package com.uxplima.uxmessentials.nametags.application;

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
 * The nametags bounded context as a first-class {@link FeatureModule}: a per-player, above-head nametag rendered
 * from operator-authored MiniMessage content under {@code modules/nametags/config.conf} through the placeholder
 * pipeline. The renderer sends a per-viewer packet text-display stack riding each wearer; that Bukkit-facing
 * machinery lands with the adapter wiring.
 *
 * <p><b>Ships disabled by default.</b> The nametag lines are operator data, so out of the box this context shows
 * nothing — an operator authors the formats and then flips {@code modules.nametags.enabled = true}. The
 * {@link #enabled(ConfigStore)} gate therefore defaults to {@code false} (like {@code tablist} and
 * {@code scoreboard}, unlike the steady-state contexts that default on). It persists nothing: the nametag is
 * entirely config-authored, so the module owns no Flyway location.
 *
 * <p>The nametag is always-on for every eligible wearer when enabled — there is no per-player visibility toggle and
 * so the module publishes no command. The render timer, the connection listener, and the renderer are constructed in
 * the adapter wiring once the module has started; the lifecycle bookkeeping here keeps {@code stop()} honest — the
 * render timer observes the running flag and exits cleanly on disable.
 */
@NullMarked
public final class NametagsModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("nametags");
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
        // The nametag is always-on when enabled — there is no per-player visibility toggle, so the module publishes
        // no command. The lines are rendered by the adapter's render timer.
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
        // nametags persists nothing: the lines and appearance are config-authored, so the module owns no Flyway
        // location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The nametag lines are operator data, so the module ships disabled — it is off until an operator authors
        // the formats and opts in via modules.conf. The default here is false (the steady-state contexts default on).
        return config.getBoolean(configRoot() + ".enabled", false);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The reconcile timer on the Scheduler port, the connection listener, and the presenter over uxmLib's packet
        // nametag renderer are constructed over ctx.kernel() in the adapter wiring; the lifecycle bookkeeping (running
        // flag, in-flight counter) is armed here so stop() is already honest.
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

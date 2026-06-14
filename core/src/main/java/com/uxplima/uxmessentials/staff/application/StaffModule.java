package com.uxplima.uxmessentials.staff.application;

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
 * The staff bounded context as a first-class {@link FeatureModule}: a STAFF-MODE-ONLY toolkit that lets a
 * staff member flip into a working "staff mode" — their real loadout is captured to the database and swapped
 * for a configurable gadget hotbar (the VANISH and EXAMINE gadgets), and a staff-only chat channel
 * ({@code /staffchat}, {@code /sc}). It owns no sanctions: it never mutes, bans, kicks, or warns. The
 * gadgets orchestrate the existing presence and playerstate modules through soft-coupled ports that degrade
 * to no-ops when those modules are off, and staff chat fans out through the messaging context's staff
 * audience.
 *
 * <p><b>Ships disabled by default.</b> Staff mode is an opt-in operator tool with operator-authored gadget
 * items, so out of the box this context does nothing — an operator authors the gadget hotbar and then flips
 * {@code modules.staff.enabled = true}. The {@link #enabled(ConfigStore)} gate therefore defaults to
 * {@code false} (like {@code nametags} and {@code tablist}, unlike the steady-state contexts that default
 * on).
 *
 * <p>It persists the captured loadout — but that {@code staff_loadout} table ships in a persistence Flyway
 * migration the persistence layer always applies, so the module declares no extra migration location of its
 * own. The toggle command, the gadget hotbar swap, the right-click gadget listener, and the soft-couple port
 * bindings are Bukkit-facing and land with the adapter wiring once the module has started; the lifecycle
 * bookkeeping here keeps {@code stop()} honest.
 */
@NullMarked
public final class StaffModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("staff");
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
        return StaffCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The gadget right-click and connection listeners are Bukkit-facing and land with the inbound
        // adapter; a disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The staff_loadout table ships in a persistence Flyway migration the persistence layer always
        // applies, so the module owns no extra Flyway location here.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // Staff mode is an opt-in tool with operator-authored gadget items, so the module ships disabled —
        // it is off until an operator authors the gadget hotbar and opts in via modules.conf. The default
        // here is false (the steady-state contexts default on).
        return config.getBoolean(configRoot() + ".enabled", false);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The toggle command handler, the gadget hotbar swap, the right-click gadget listener, and the
        // soft-couple port bindings are constructed over ctx.kernel() in the adapter wiring; the lifecycle
        // bookkeeping (running flag, in-flight counter) is armed here so stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; the adapter's gadget machinery observes this and exits on stop. */
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

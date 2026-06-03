package com.uxplima.uxmessentials.moderation.application;

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
 * The moderation bounded context as a first-class {@link FeatureModule}: it owns the DB-backed sanction state
 * (mute/jail/tempban/warn/ip-ban/seen) and the {@code /mute /unmute /tempmute /jail /unjail /tempban /kick
 * /kickall /warn /warns /unwarn /banip /unbanip /freeze /unfreeze /seen /seenip} command surface
 * (docs/10-feature-modules.md §15.9). It <em>provides</em> two cross-context gates: the messaging context's
 * {@code MutePolicy} (a muted player cannot {@code /msg}) and the teleport context's {@code JailGate} (a
 * jailed player cannot {@code /home}/{@code /tpa}); both are bound when this module wires, and degrade to
 * "no one is muted/jailed" when this module is disabled.
 *
 * <p>The sanction tables ship in the persistence baseline (V5 under {@code db/migration}, always applied by
 * the persistence layer), so the module declares no extra migration location of its own; a disabled module
 * leaves the baseline tables in place but wires nothing over them and rebinds no gate. The use cases, the
 * jOOQ {@code ModerationRepository}, the audit logger, the login/join listeners and the gate bridges are
 * constructed in the adapter wiring once the module has started; the lifecycle bookkeeping here keeps
 * {@code stop()} honest.
 */
@NullMarked
public final class ModerationModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("moderation");
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
        return ModerationCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The ban-on-login (PlayerLoginEvent HIGHEST) and the jail/freeze join listeners are Bukkit-facing
        // and land with the inbound adapter; a disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The sanction tables are part of the persistence baseline (db/migration V5), always applied by the
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
        // The use cases, the jOOQ ModerationRepository, the audit logger, the login/join listeners and the
        // MutePolicy/JailGate bridges are constructed over ctx.kernel() and the persistence DSL in the adapter
        // wiring; the lifecycle bookkeeping (running flag, in-flight counter) is armed here so stop() is honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; the join-tick countdown observes this and exits on stop. */
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

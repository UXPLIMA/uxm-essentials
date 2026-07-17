package com.uxplima.uxmessentials.invrollback.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.invrollback.adapter.inbound.listener.SnapshotCaptureListener;
import com.uxplima.uxmessentials.invrollback.application.CaptureSnapshot;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackConfig;
import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.persistence.invrollback.SnapshotRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the invrollback context's adapter and use case over the injected kernel ports and the persistence
 * DSL, and produces the death/logout capture listener the plugin registers. The snapshot repository is the jOOQ
 * adapter built by {@link SnapshotRepositories} over the shared persistence DSL (so this module never names a jOOQ
 * type); the {@link CaptureSnapshot} use case saves through
 * it and bounds a player's snapshots to the configured count at write time; the listener reads the live inventory
 * on the tick thread and hops the DB write off it through the injected {@code Scheduler}. The context persists to
 * the shared pool but holds no runtime state of its own, so there is nothing to drain on stop — a disabled module
 * simply registers no listener.
 */
@NullMarked
public final class InvrollbackWiring {

    private InvrollbackWiring() {}

    /** Build the invrollback adapter and use case from {@code ctx} and {@code persistence}, ready to register. */
    public static Wired wire(ModuleContext ctx, Persistence persistence) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        InvrollbackConfig config = InvrollbackConfig.from(ctx.config());
        SnapshotRepository repository = SnapshotRepositories.jooq(persistence);
        CaptureSnapshot capture = new CaptureSnapshot(repository, config.maxPerPlayer());
        SnapshotCaptureListener listener = new SnapshotCaptureListener(
                capture,
                ctx.kernel().scheduler(),
                Clock.systemUTC(),
                config.captureOnDeath(),
                config.captureOnLogout(),
                config.includeEnderchest());
        return new Wired(List.of(listener));
    }

    /**
     * Everything the invrollback module contributes once wired: the death/logout capture listener the plugin
     * registers.
     *
     * @param listeners the Bukkit listeners to register
     */
    public record Wired(List<Listener> listeners) {
        public Wired {
            listeners = List.copyOf(listeners);
        }
    }
}

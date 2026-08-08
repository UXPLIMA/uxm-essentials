package com.uxplima.uxmessentials.vote.application;

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
 * The vote bounded context as a first-class {@link FeatureModule}: it bridges an upstream Votifier vote
 * to per-vote reward commands, a thank-you broadcast, and a global vote-party counter that fires party
 * rewards for every online player once a configured threshold is reached; a vote for an offline player
 * is queued and paid out on their next join. It owns {@code /vote} (the configured vote links, plus a
 * {@code testreward} subcommand) and {@code /voteparty} (the party progress). The Votifier event
 * listener and the join handler are Bukkit-facing and land with the adapter wiring.
 *
 * <p><b>Ships disabled by default.</b> A vote reaches the server through Votifier and pays out of a reward list
 * the operator writes, so a server that has installed neither has nothing for this to do and
 * {@link #enabled(ConfigStore)} defaults to {@code false}. Once on, the Votifier soft-depend is handled in the
 * adapter listener behind a plugin-present guard, so {@code /vote} and {@code /voteparty} work even when Votifier
 * is absent; only the vote listener is then a no-op.
 *
 * <p>It persists the global party counter and the per-player offline reward queue, but those tables ship
 * in the persistence V15 baseline ({@code db/migration}), which the persistence layer always applies, so
 * the module declares no extra migration location of its own.
 */
@NullMarked
public final class VoteModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("vote");
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
        return VoteCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The Votifier vote listener and the join handler are Bukkit-facing and land with the inbound adapter;
        // a disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The vote_party and vote_queue tables are part of the persistence V15 baseline (db/migration), always
        // applied by the persistence layer, so the module owns no additional Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships DISABLED: a vote arrives through Votifier, and a server that has not installed it and
        // listed itself on a voting site has nothing for this to do.
        return config.getBoolean(configRoot() + ".enabled", false);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The use cases, the jOOQ repository over persistence.dsl(), the reward dispatcher, and the listeners
        // are constructed over ctx.kernel() in the adapter wiring; the lifecycle bookkeeping is armed here so
        // stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; async vote handling observes this and exits on stop. */
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

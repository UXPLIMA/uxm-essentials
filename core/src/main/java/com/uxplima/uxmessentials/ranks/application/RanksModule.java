package com.uxplima.uxmessentials.ranks.application;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The ranks bounded context as a first-class {@link FeatureModule}: rankup / prestige / autorank progression,
 * tracking the player's current rank with a DB-backed pointer of our own rather than reading a permission
 * plugin's group. The module stands up the identity, the enable gate, the ladder ({@link RankLadders}) and the
 * {@code player_ranks} store; the {@code /rankup} and {@code /prestige} verbs, their requirement/action evaluation,
 * the autorank scan and the {@code /ranks} GUI are contributed through the adapter wiring.
 *
 * <p><b>Ships enabled by default.</b> The module is inert until an operator authors a ladder in
 * {@code ranks.conf}, so like the steady-state contexts {@link #enabled(ConfigStore)} defaults to {@code true};
 * an operator flips {@code modules.ranks.enabled = false} to turn it off.
 *
 * <p>The {@code player_ranks} table ships in the persistence baseline (a {@code V##} migration under
 * {@code db/migration}, always applied by the persistence layer), so the module declares no extra Flyway
 * location of its own. The module publishes no declarative command or listener, since the adapter wiring
 * contributes them, and a disabled module wires zero either way (the {@code FeatureModule} contract). The
 * repository, ladder and read use case are constructed over the persistence DSL in the adapter wiring; the
 * lifecycle bookkeeping here keeps {@link #stop()} honest.
 */
@NullMarked
public final class RanksModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("ranks");

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
        // No declarative listener: the adapter wiring registers the autorank scan and any interaction listeners.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The player_ranks table is part of the persistence baseline (db/migration V74), always applied by the
        // persistence layer, so the module owns no additional Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships enabled but inert (nothing happens until a ladder is authored); an operator opts out
        // via modules/ranks/config.conf.
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The jOOQ PlayerRankRepository, the parsed RankLadder and the CurrentRank use case are constructed over
        // ctx and the persistence DSL in the adapter wiring; the running flag is armed here so stop() is honest.
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** True while the module is started; background work observes this and exits on stop. */
    public boolean isRunning() {
        return running;
    }
}

package com.uxplima.uxmessentials.trade.application;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The trade bounded context as a first-class {@link FeatureModule}: player-to-player trading ({@code /trade}) with a
 * live dual-inventory window, money, a request/accept flow, and cross-server escrow. The module stands up the
 * identity, the enable gate, and the lifecycle bookkeeping so the pure {@code TradeSession} state machine and the
 * typed {@link TradeConfig} can be wired behind it.
 *
 * <p><b>Ships enabled by default</b> like the other steady-state contexts, so {@link #enabled(ConfigStore)} defaults
 * to {@code true}; an operator flips {@code modules.trade.enabled = false} to turn the feature off. Same-server trades
 * hold their session purely in memory ({@code TradeSession}), and the cross-server escrow and item-return tables ship
 * in the persistence baseline, so the module owns no Flyway location of its own.
 *
 * <p>The module publishes no declarative command or listener: the {@code /trade} verbs and the window listeners come
 * from the inbound adapter, and a disabled module wires zero either way (the {@code FeatureModule} contract). The adapter clears its registry on {@link #stop()} so a disable or reload leaves no
 * residual runtime state.
 */
@NullMarked
public final class TradeModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("trade");

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
        // The trade-window inventory listeners (place/take/close/quit) come from the inbound adapter; a disabled
        // module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // A same-server trade is transient in-memory state held in a TradeSession, and the escrow and item-return
        // tables ship in the persistence baseline, so the module owns no Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships enabled: a fresh install offers /trade out of the box, like the steady-state contexts. An
        // operator opts out via modules/trade/config.conf.
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The typed config and the session registry are constructed over ctx in the adapter wiring; the lifecycle
        // bookkeeping (running flag) is armed here so stop() is honest even if the wiring fails part way.
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

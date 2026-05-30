package com.uxplima.uxmessentials.shared.application.module;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * A bounded context as a first-class, independently enableable unit.
 *
 * <p>Lifecycle: {@link #enabled(ConfigStore)} is consulted first, then {@link #loadCondition()};
 * only a module that is both enabled and loadable is {@link #start(ModuleContext) started},
 * contributes its {@link #commands()} / {@link #listeners()}, and runs its {@link #migrations()}.
 * {@link #stop()} must leave zero residual runtime state: drain in-flight async work, cancel every
 * scheduled handle the module opened, drop caches and per-player maps, and deregister commands and
 * listeners. A disabled module instantiates zero adapters, registers zero commands and listeners,
 * runs zero migrations, and holds zero runtime state.
 *
 * <p>Implementations are pure application code: they describe what to wire via the platform-neutral
 * spec records ({@link CommandSpec}, {@link ListenerFactory}, {@link MigrationSet}) and construct
 * adapters only inside {@code start}.
 */
public interface FeatureModule {

    /** Stable identifier: {@code homes}, {@code economy}, … Equals the context's package name. */
    ModuleId id();

    /** HOCON path this module owns: {@code modules.<id>} in {@code modules.conf}. */
    String configRoot();

    /** Brigadier command specs; registered only when enabled. Empty when none. */
    List<CommandSpec> commands();

    /** Bukkit listener factories; registered only when enabled. Empty when none. */
    List<ListenerFactory> listeners();

    /** Flyway migration locations; run only when enabled. Empty when none. */
    List<MigrationSet> migrations();

    /** The enable gate, read from {@code modules.conf} via the config port. Default is {@code true}. */
    boolean enabled(ConfigStore config);

    /**
     * Capability gate beyond the operator switch (§2.5 of the feature-module design). The default
     * is "always loadable"; a module that needs a soft dependency or runtime flavour overrides this
     * to return a human-readable reason when the environment cannot satisfy it.
     */
    default LoadCondition loadCondition() {
        return probe -> Optional.empty();
    }

    /** Construct adapters and acquire runtime state. Called once per enable. */
    void start(ModuleContext ctx);

    /**
     * Release everything {@link #start} acquired, in reverse order, and return only once quiescent:
     * drain in-flight async work within a bounded wait and cancel every scheduled handle the module
     * opened. A clean stop leaves zero orphaned tasks and zero in-memory state; persisted rows are
     * untouched.
     */
    void stop();
}

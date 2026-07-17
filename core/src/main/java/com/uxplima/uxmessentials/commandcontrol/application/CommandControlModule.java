package com.uxplima.uxmessentials.commandcontrol.application;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The command-control bounded context as a first-class {@link FeatureModule}: PlHidePro / CommandWhitelist-parity
 * gating of which commands a player may run, built in with no separate plugin. Phase 1 is the command
 * whitelist/blacklist and the custom "unknown command" deny message; the tab-completion filter, the plugin-hide, and
 * the namespace-bypass block land in the later phases.
 *
 * <p><b>Ships enabled by default</b> but inert: the bundled config is a blacklist with empty lists, so an enabled
 * module gates nothing until an operator names commands. An operator turns the whole feature off with
 * {@code modules.commandcontrol.enabled = false}. It persists nothing — the rule set is derived from config — so the
 * module owns no Flyway location.
 *
 * <p>The gate is a single {@code PlayerCommandPreprocessEvent} listener contributed through the adapter wiring (it
 * needs the live player, the permission check, and the group lookup), so this module publishes no declarative command
 * or listener; a disabled module wires zero of them (the {@code FeatureModule} contract).
 */
@NullMarked
public final class CommandControlModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("commandcontrol");

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
        // The gate is a listener, not a command; the /uxmess cc reload hook lands with the later phases.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The PlayerCommandPreprocessEvent gate is Bukkit-facing and needs the group/permission lookups, so it is
        // contributed through the adapter wiring rather than this declarative list.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // command-control persists nothing: the rule set is derived from config, so the module owns no Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** True while the module is started; the adapter's listener is registered only between start and stop. */
    public boolean isRunning() {
        return running;
    }
}

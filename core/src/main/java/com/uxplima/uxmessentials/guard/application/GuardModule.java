package com.uxplima.uxmessentials.guard.application;

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
 * The guard bounded context: region protection on its own engine, replacing the WorldGuard front end the
 * {@code regions} module was.
 *
 * <p>The {@code /guard} command, the protection listeners, the movement tracker and the menus are Bukkit facing
 * and built in the adapter wiring once the module starts. The guard tables ship in the persistence baseline
 * migration {@code V88__guard.sql}, so the module declares no Flyway location of its own. It ships enabled: with
 * no regions and no world defaults it registers no protection listener at all, so an idle module costs nothing.
 */
@NullMarked
public final class GuardModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("guard");

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
        // /guard (and /bolge, /rg) are contributed by the adapter wiring, like every Bukkit facing command tree.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The protection listeners are registered and unregistered by the wiring as rules come and go.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
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

    /** True while the module is started. */
    public boolean isRunning() {
        return running;
    }
}

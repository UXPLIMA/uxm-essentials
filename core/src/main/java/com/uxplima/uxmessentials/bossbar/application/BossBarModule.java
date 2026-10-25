package com.uxplima.uxmessentials.bossbar.application;

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
 * The boss bar context: bars that stay up, bars that come and go with an event's window, and bars a command announces
 * for a while, written by the operator under {@code modules/bossbar/config.conf} and drawn for each player in their
 * own language. It owns {@code /bossbars}, and the renderer, the refresh loop and the listener are built in the
 * adapter wiring.
 *
 * <p>Ships disabled, like the sidebar and the tab list: a bar on every screen is a visual decision, and a dedicated
 * HUD plugin draws the same surface. It persists nothing in the database: a player's choice to hide their bars is
 * kept with the player, and an announcement lasts seconds or minutes, so a restart may end it.
 */
@NullMarked
public final class BossBarModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("bossbar");

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
        return BossBarCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", false);
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

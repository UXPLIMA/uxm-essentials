package com.uxplima.uxmessentials.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import com.uxplima.uxmessentials.bootstrap.di.CloseableResources;
import com.uxplima.uxmessentials.bootstrap.di.PluginModule;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The thin {@link JavaPlugin} shell: wires the DI graph on enable, closes it on disable.
 *
 * <p>The plugin instance is never exposed via a static accessor — services are constructed and
 * injected by {@link PluginModule}, which is the only holder of this reference. The {@code COMMANDS}
 * lifecycle handler publishes the already-module-filtered command set built during wiring, so a
 * disabled module's command literal never reaches the dispatcher.
 */
@NullMarked
public final class UxmEssentialsPlugin extends JavaPlugin {

    private @Nullable CloseableResources resources;

    @Override
    public void onEnable() {
        CloseableResources wired = PluginModule.wire(this);
        this.resources = wired;
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            wired.commands()
                    .forEach(command -> registrar.register(command.build(), command.description(), command.aliases()));
        });
    }

    @Override
    public void onDisable() {
        CloseableResources wired = this.resources;
        if (wired != null) {
            wired.close(); // stops every started module in reverse wiring order
            this.resources = null;
        }
    }
}

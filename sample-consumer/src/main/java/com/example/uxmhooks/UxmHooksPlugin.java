package com.example.uxmhooks;

import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;

/**
 * A minimal plugin that hooks into uxmEssentials, written the way the documentation tells you to write one.
 *
 * <p>There is no {@code depend} on uxmEssentials and no load-order assumption. {@link UxmEssentialsApi#whenReady}
 * runs the callback immediately if uxmEssentials is already up and as soon as it enables otherwise, so the same code
 * works whichever order the server happens to load the two plugins in. Listeners can be registered right away,
 * because Bukkit does not care whether the plugin firing an event is loaded yet.
 */
public final class UxmHooksPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new UxmHooksListener(getLogger()), this);

        UxmEssentialsApi.whenReady(api -> {
            getLogger().info("uxmEssentials " + api.version() + " is ready");
            if (api.isModuleEnabled("homes")) {
                getLogger().info("the homes module is on, so the home events will fire");
            }
        });
    }
}

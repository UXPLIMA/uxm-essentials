package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.lang.reflect.Method;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Resolves an {@code ei:<id>} spec to its ExecutableItems item. The chain is the plugin's own static facade
 * {@code com.ssomar.score.api.executableitems.ExecutableItemsAPI.getExecutableItemsManager()}, whose
 * {@code getExecutableItem(String)} answers an {@link Optional} of the configured item; building it with
 * {@code buildItem(1, Optional.empty())} yields the finished {@link ItemStack}.
 *
 * <p>The empty player argument is deliberate. An ExecutableItem may interpolate the holder into its own name and
 * lore, but a menu icon is rendered for whoever is looking at the menu and our own placeholder pass has already
 * run over the spec, so the item is built unattached and stays the same for every viewer.
 *
 * <p>No {@code com.ssomar} type is named here (the SDK is reached only by string class-name through reflection),
 * so a server without ExecutableItems loads none of its classes and the present-guard in
 * {@link ReflectiveItemProvider} short-circuits before any lookup runs.
 */
final class ExecutableItemsIconProvider extends ReflectiveItemProvider {

    private static final String PLUGIN_NAME = "ExecutableItems";
    private static final String PREFIX = "ei:";
    private static final String API_CLASS = "com.ssomar.score.api.executableitems.ExecutableItemsAPI";
    private static final String ITEM_CLASS = "com.ssomar.score.api.executableitems.config.ExecutableItemInterface";

    ExecutableItemsIconProvider(Server server, Logger log) {
        super(PLUGIN_NAME, PREFIX, server, log);
    }

    @Override
    protected @Nullable ItemStack lookup(String id) throws ReflectiveOperationException {
        // Both handles come from the declared API types rather than from the returned objects: ExecutableItems
        // hands back internal implementation classes, and a handle taken from one of those is unreachable here.
        Method accessor = Class.forName(API_CLASS).getMethod("getExecutableItemsManager");
        Object manager = accessor.invoke(null);
        if (manager == null) {
            return null;
        }
        Object found = accessor.getReturnType()
                .getMethod("getExecutableItem", String.class)
                .invoke(manager, id);
        if (!(found instanceof Optional<?> optional) || optional.isEmpty()) {
            return null;
        }
        Object built = Class.forName(ITEM_CLASS)
                .getMethod("buildItem", int.class, Optional.class)
                .invoke(optional.get(), 1, Optional.<Player>empty());
        return built instanceof ItemStack stack ? stack : null;
    }
}

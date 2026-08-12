package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.lang.reflect.Method;

import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a {@code craftengine:<id>} spec to its CraftEngine custom item. CraftEngine exposes a static facade
 * {@code net.momirealms.craftengine.bukkit.api.CraftEngineItems}, whose {@code byId(String)} returns the item
 * definition (or {@code null} for an unknown id). The id may be fully qualified ({@code default:topaz}) or the bare
 * path ({@code topaz}), which the facade resolves against every loaded namespace, so both {@code craftengine:topaz}
 * and {@code craftengine:default:topaz} work.
 *
 * <p>Turning a definition into a stack is the one place CraftEngine's shape differs from its siblings: the build
 * method takes CraftEngine's own player type (nullable, for a build with no viewer) and returns CraftEngine's item
 * wrapper rather than a Bukkit {@link ItemStack}. Both hops are therefore chosen structurally, by parameter and
 * return type, instead of by a named signature: the wrapper is unwrapped through whichever no-argument accessor
 * hands back an {@link ItemStack}. That also carries the older releases, where the same call was named
 * {@code buildItemStack} and returned the stack directly.
 *
 * <p>No {@code net.momirealms} type is named here: the SDK is reached only by string class-name through reflection,
 * so a server without CraftEngine loads none of its classes and the present-guard in {@link ReflectiveItemProvider}
 * short-circuits before any lookup runs.
 */
final class CraftEngineIconProvider extends ReflectiveItemProvider {

    private static final String PLUGIN_NAME = "CraftEngine";
    private static final String PREFIX = "craftengine:";
    private static final String ITEMS_CLASS = "net.momirealms.craftengine.bukkit.api.CraftEngineItems";
    private static final String KEY_CLASS = "net.momirealms.craftengine.core.util.Key";

    /** The simple name of CraftEngine's own player type, which the viewer-less build overload takes. */
    private static final String PLAYER_TYPE = "Player";

    CraftEngineIconProvider(Server server, Logger log) {
        super(PLUGIN_NAME, PREFIX, server, log);
    }

    @Override
    protected @Nullable ItemStack lookup(String id) throws ReflectiveOperationException {
        Object definition = definition(Class.forName(ITEMS_CLASS), id);
        if (definition == null) {
            return null;
        }
        Object built = build(definition).invoke(definition, new Object[] {null});
        return built == null ? null : unwrap(built);
    }

    /** The definition for {@code id}, taking the string lookup and falling back to the older key-only one. */
    private static @Nullable Object definition(Class<?> items, String id) throws ReflectiveOperationException {
        try {
            return items.getMethod("byId", String.class).invoke(null, id);
        } catch (NoSuchMethodException olderApi) {
            Class<?> key = Class.forName(KEY_CLASS);
            Object parsed = key.getMethod("of", String.class).invoke(null, id);
            return items.getMethod("byId", key).invoke(null, parsed);
        }
    }

    /**
     * The build overload that takes a viewer, which is what a menu icon wants: it is built for nobody in particular
     * (the argument is nullable), so the icon carries no player-specific state. Chosen by its parameter type rather
     * than by name, so the rename between CraftEngine releases does not matter.
     */
    private static Method build(Object definition) throws NoSuchMethodException {
        for (Method method : definition.getClass().getMethods()) {
            if (method.getParameterCount() == 1
                    && PLAYER_TYPE.equals(method.getParameterTypes()[0].getSimpleName())
                    && method.getName().startsWith("buildItem")) {
                return method;
            }
        }
        throw new NoSuchMethodException("no viewer-less build method on " + definition.getClass());
    }

    /** The Bukkit stack inside CraftEngine's item wrapper, or the value itself when it already is one. */
    private static @Nullable ItemStack unwrap(Object built) throws ReflectiveOperationException {
        if (built instanceof ItemStack stack) {
            return stack;
        }
        for (Method method : built.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && ItemStack.class.equals(method.getReturnType())) {
                return (ItemStack) method.invoke(built);
            }
        }
        throw new NoSuchMethodException("no ItemStack accessor on " + built.getClass());
    }
}

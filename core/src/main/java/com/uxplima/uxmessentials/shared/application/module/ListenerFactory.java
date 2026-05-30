package com.uxplima.uxmessentials.shared.application.module;

import java.util.Objects;
import java.util.function.Function;

/**
 * Platform-neutral description of one Bukkit listener a module contributes.
 *
 * <p>It is a factory rather than a listener instance because the listener needs injected ports and
 * must not exist on a disabled module. The bukkit-adapter calls {@link #create} with the started
 * module's context and registers the returned instance with the plugin manager; a disabled module's
 * factory is never called, so its event handler imposes zero cost on the hot path.
 *
 * <p>The produced object is typed as {@link Object} here because {@code org.bukkit.event.Listener}
 * is a Bukkit type the kernel is forbidden from importing; the adapter narrows it back to a
 * {@code Listener} at the registration boundary.
 */
public record ListenerFactory(Function<ModuleContext, Object> factory) {

    public ListenerFactory {
        Objects.requireNonNull(factory, "factory");
    }

    /** Builds the listener instance from the module's runtime context. */
    public Object create(ModuleContext ctx) {
        return factory.apply(Objects.requireNonNull(ctx, "ctx"));
    }
}

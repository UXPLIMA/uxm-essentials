package com.uxplima.uxmessentials.poses.adapter.outbound;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Server;

import org.jspecify.annotations.NullMarked;

/**
 * Registers the poses custom flags ({@code sit} / {@code playersit} / {@code pose} / {@code crawl}) with WorldGuard's
 * flag registry. WorldGuard locks that registry the moment it enables, so registration must happen in the load phase,
 * <em>before</em> any plugin is enabled — {@code UxmEssentialsPlugin.onLoad()} is the correct hook (a Paper
 * {@code PluginBootstrap} runs before WorldGuard has even created its registry, so it is too early). WorldGuard is a
 * {@code load: BEFORE} soft-depend, so by our {@code onLoad} its registry already exists.
 *
 * <p>Guarded so it is a silent no-op when WorldGuard is absent: the present-check on the plugin manager short-circuits
 * before any {@code Class.forName}, so a WorldGuard-less server loads none of the {@code com.sk89q} classes named here
 * only by string. Registration is best-effort — a flag already present (a plugin-manager reload re-running
 * {@code onLoad}) is skipped, and any reflective failure (a version bump moving the registry API) is logged once and
 * degraded to a no-op, leaving the gate to fail open. The flags default to ALLOW, so registering them is inert until
 * an operator sets one DENY on a region.
 */
@NullMarked
public final class WorldGuardPoseFlagRegistrar {

    private WorldGuardPoseFlagRegistrar() {}

    /** Register the four custom flags with WorldGuard if it is present; otherwise do nothing. */
    public static void register(Server server, Logger log) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");
        // During the load phase a plugin is known to the manager but not yet "enabled", so we test presence, not
        // enabled-state — isPluginEnabled would be false here for WorldGuard even though its registry is ready.
        if (server.getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }
        try {
            registerAll();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            log.log(Level.WARNING, "Could not register poses WorldGuard flags: " + failure, failure);
        }
    }

    private static void registerAll() throws ReflectiveOperationException {
        Object instance = Class.forName("com.sk89q.worldguard.WorldGuard")
                .getMethod("getInstance")
                .invoke(null);
        Object registry = instance.getClass().getMethod("getFlagRegistry").invoke(instance);
        Constructor<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag")
                .getConstructor(String.class, boolean.class);
        for (String name : WorldGuardPoseFlags.FLAG_NAMES) {
            registerOne(registry, stateFlag, name);
        }
    }

    /** Register one flag, skipping it when a flag of that name is already present so a re-run never conflicts. */
    private static void registerOne(Object registry, Constructor<?> stateFlag, String name)
            throws ReflectiveOperationException {
        Object existing = registry.getClass().getMethod("get", String.class).invoke(registry, name);
        if (existing != null) {
            return;
        }
        Object flag = stateFlag.newInstance(name, true); // default ALLOW — inert until a region sets it DENY
        Class<?> flagType = Class.forName("com.sk89q.worldguard.protection.flags.Flag");
        registry.getClass().getMethod("register", flagType).invoke(registry, flag);
    }
}

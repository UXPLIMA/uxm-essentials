package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;

import com.uxplima.uxmessentials.shared.application.permission.PermissionCatalog;
import com.uxplima.uxmessentials.shared.application.permission.PermissionSpec;
import org.jspecify.annotations.NullMarked;

/**
 * Tells the server about every node the catalogue declares.
 *
 * <p>Registration is what makes a node visible outside our own code: a permission plugin completes and suggests
 * registered nodes, {@code /help}-style listings can read the description, and an operator browsing their permission
 * plugin sees the whole surface rather than the subset somebody remembered to write into a file. This used to be the
 * {@code permissions:} block of {@code paper-plugin.yml}, which is why it drifted; the catalogue is the one place
 * now and this hands it to the server.
 *
 * <p>Only the fixed nodes are registered. A family is a shape completed at runtime from a number or a name
 * ({@code uxmessentials.home.limit.<n>}), so there is nothing finite to hand over; those live in the catalogue for
 * the reference page and the in-game listing, and the server never needs to know them.
 *
 * <p>Every node is registered, including those of a disabled module, because an operator sets permissions up before
 * they switch a module on, not after. A node another plugin already registered is left alone rather than replaced:
 * whoever got there first owns it, and quietly rewriting their description would be worse than not describing ours.
 */
@NullMarked
public final class CatalogPermissions {

    private final PluginManager plugins;
    private final Logger logger;
    private final List<Permission> registered = new ArrayList<>();

    public CatalogPermissions(PluginManager plugins, Logger logger) {
        this.plugins = Objects.requireNonNull(plugins, "plugins");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Register every fixed node the catalogue declares, and return how many were new to the server. */
    public int register() {
        for (PermissionSpec spec : PermissionCatalog.registrable()) {
            if (plugins.getPermission(spec.node()) != null) {
                continue;
            }
            try {
                Permission permission =
                        new Permission(spec.node(), spec.description(), BukkitPermissionDefaults.of(spec.fallback()));
                plugins.addPermission(permission);
                registered.add(permission);
            } catch (RuntimeException refused) {
                // One bad node must not cost the rest of the surface its registration.
                logger.log(Level.WARNING, "could not register permission " + spec.node(), refused);
            }
        }
        return registered.size();
    }

    /** Take back exactly what this registered, leaving anything another plugin owns untouched. */
    public void unregister() {
        registered.forEach(plugins::removePermission);
        registered.clear();
    }
}

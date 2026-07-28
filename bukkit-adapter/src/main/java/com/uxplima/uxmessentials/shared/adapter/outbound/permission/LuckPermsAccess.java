package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import org.jspecify.annotations.NullMarked;

/**
 * The single answer to "is LuckPerms usable on this server".
 *
 * <p>Three separate features read LuckPerms: the kernel's quota meta, the chat renderer's prefix/suffix, and the
 * command gate's group lookup. Each used to carry its own copy of the plugin name and its own presence test, which
 * meant the same question was asked three ways and could be answered three ways: two of them tested only that the
 * plugin was installed, never that it had actually enabled, so a LuckPerms that failed its own startup would still
 * have been treated as live.
 *
 * <p>This class deliberately names no {@code net.luckperms} type anywhere: not in a field, not in a signature, not in
 * a method body. That is what makes it safe to call from code that has not yet checked anything, which is the whole
 * point of a presence test. Acquiring the API stays with the caller, immediately past this guard and inside a plain
 * {@code new}, because that is the one shape the JVM is guaranteed not to link early. A "build it for me" helper
 * taking a lambda would read better and be wrong: evaluating the lambda links its implementation method, which loads
 * the LuckPerms-touching class whether or not the guard passed.
 */
@NullMarked
public final class LuckPermsAccess {

    /** The Bukkit plugin name, matching the soft-depend declared in {@code paper-plugin.yml}. */
    public static final String PLUGIN = "LuckPerms";

    private LuckPermsAccess() {}

    /**
     * Whether LuckPerms is installed <em>and</em> enabled. Callers may only touch {@code net.luckperms} symbols once
     * this has returned true, and must do so through a direct constructor call rather than a deferred one.
     */
    public static boolean isPresent(Server server) {
        Objects.requireNonNull(server, "server");
        Plugin plugin = server.getPluginManager().getPlugin(PLUGIN);
        return plugin != null && plugin.isEnabled();
    }
}

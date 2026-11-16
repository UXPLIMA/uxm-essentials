package com.uxplima.uxmessentials.shared.application.permission;

/**
 * What {@link CatalogDefaults} needs to know about whoever a node is checked for: a player, the console, any sender.
 *
 * <p>The three questions are the ones the server's own permission object answers, kept here in plain terms so the
 * rule that picks between an explicit setting and the catalogue's default stays application code and can be
 * exercised without a server.
 */
public interface PermissionHolder {

    /** Whether {@code node} has been granted or denied to this holder, by the server or a permission plugin. */
    boolean isPermissionSet(String node);

    /** The server's own answer for {@code node}: the setting when there is one, the server's default otherwise. */
    boolean hasPermission(String node);

    /** Whether this holder is a server operator. */
    boolean isOp();
}

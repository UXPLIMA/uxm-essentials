package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import com.uxplima.uxmessentials.shared.application.permission.PermissionDefault;
import org.jspecify.annotations.NullMarked;

/**
 * Translates the catalogue's platform-neutral default onto the server's own type.
 *
 * <p>The catalogue is application code and cannot name {@code org.bukkit.permissions.PermissionDefault}, so the two
 * enums are declared separately and meet here. They carry the same four values, and this is the only place that
 * knows it.
 */
@NullMarked
final class BukkitPermissionDefaults {

    private BukkitPermissionDefaults() {}

    static org.bukkit.permissions.PermissionDefault of(PermissionDefault fallback) {
        return switch (fallback) {
            case TRUE -> org.bukkit.permissions.PermissionDefault.TRUE;
            case FALSE -> org.bukkit.permissions.PermissionDefault.FALSE;
            case OP -> org.bukkit.permissions.PermissionDefault.OP;
            case NOT_OP -> org.bukkit.permissions.PermissionDefault.NOT_OP;
        };
    }
}

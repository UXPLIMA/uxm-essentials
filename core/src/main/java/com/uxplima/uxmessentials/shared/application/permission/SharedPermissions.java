package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

/**
 * The permission table for the cross-cutting surfaces no single module owns. Data, not logic: one row per node, read
 * by {@link PermissionCatalog} and through it by the server registration, the reference page and the in-game listing.
 */
final class SharedPermissions {

    private SharedPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(shared()).flatMap(List::stream).toList();
    }

    private static List<PermissionSpec> shared() {
        return List.of(
                PermissionSpec.shared(
                        "uxmessentials.admin", "Access to the /uxmess administration root.", PermissionDefault.OP),
                PermissionSpec.shared(
                        "uxmessentials.admin.backup",
                        "/backup to snapshot the plugin data directory on demand.",
                        PermissionDefault.OP),
                PermissionSpec.shared(
                        "uxmessentials.admin.import",
                        "/uxmess import <plugin>: run the one-shot data import from another essentials plugin.",
                        PermissionDefault.OP),
                PermissionSpec.shared(
                        "uxmessentials.admin.permissions",
                        "/uxmess permissions [area] [page] and /uxmess permissions export: read the permission catalogue in game or write it to a file.",
                        PermissionDefault.OP),
                PermissionSpec.shared(
                        "uxmessentials.admin.reload", "Reload all modules via /uxmess reload.", PermissionDefault.OP),
                PermissionSpec.sharedFamily(
                        "uxmessentials.cooldown.<feature>.<seconds>",
                        "The wait between uses of one rate-limited feature, in seconds; the shortest tier held wins and 0 removes the wait.",
                        PermissionDefault.TRUE,
                        PermissionShape.TIER),
                PermissionSpec.sharedFamily(
                        "uxmessentials.cooldown.bypass.<feature>",
                        "Skip the cooldown on one rate-limited feature entirely (tp, rtp, kit, poses).",
                        PermissionDefault.OP,
                        PermissionShape.LABEL),
                PermissionSpec.shared(
                        "uxmessentials.gui", "/uxmess gui to open the module management hub.", PermissionDefault.OP),
                PermissionSpec.shared(
                        "uxmessentials.help", "/help to list the commands you can use.", PermissionDefault.TRUE),
                PermissionSpec.shared(
                        "uxmessentials.lang.use",
                        "/lang to set or clear your personal language override.",
                        PermissionDefault.TRUE),
                PermissionSpec.shared(
                        "uxmessentials.update.notify",
                        "Receive the join-time notice when a newer plugin version is available.",
                        PermissionDefault.OP));
    }
}

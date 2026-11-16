package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.permission.ContentPermissions;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.permission.PlayerstatePermissions;

/** Places a player goes back to: homes, warps, player warps, kits and worlds. Data, not logic: one row per command, read by {@link CommandSpecCatalog}. */
final class DestinationCommandSpecs {

    private static final ModuleId HOMES = ModuleId.of("homes");
    private static final ModuleId WARPS = ModuleId.of("warps");
    private static final ModuleId PLAYERWARPS = ModuleId.of("playerwarps");
    private static final ModuleId KITS = ModuleId.of("kits");
    private static final ModuleId WORLDS = ModuleId.of("worlds");

    private DestinationCommandSpecs() {}

    static List<CommandSpec> all() {
        return List.of(
                CommandSpec.of(HOMES, "home", DestinationPermissions.HOME_USE, "h", "homes"),
                CommandSpec.of(WARPS, "warp", DestinationPermissions.WARP_USE, "wp", "warps"),
                CommandSpec.of(PLAYERWARPS, "pwarp", DestinationPermissions.PWARP_USE),
                CommandSpec.of(PLAYERWARPS, "pwarps", DestinationPermissions.PWARP_LIST),
                CommandSpec.of(PLAYERWARPS, "setpwarp", DestinationPermissions.PWARP_SET),
                CommandSpec.of(KITS, "kit", ContentPermissions.KIT_USE, "k", "kits"),
                CommandSpec.of(WORLDS, "worlds", PlayerstatePermissions.WORLD_USE));
    }
}

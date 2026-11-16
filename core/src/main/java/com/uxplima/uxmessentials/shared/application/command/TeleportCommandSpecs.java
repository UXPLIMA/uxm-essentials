package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.permission.TeleportPermissions;

/** Teleport requests, admin teleports, positional jumps, spawn, back and random teleport. Data, not logic: one row per command, read by {@link CommandSpecCatalog}. */
final class TeleportCommandSpecs {

    private static final ModuleId TELEPORT = ModuleId.of("teleport");

    private TeleportCommandSpecs() {}

    static List<CommandSpec> all() {
        return List.of(
                CommandSpec.of(TELEPORT, "ascend", TeleportPermissions.TP_VERTICAL),
                CommandSpec.of(TELEPORT, "back", TeleportPermissions.BACK_USE, "return"),
                CommandSpec.of(TELEPORT, "bottom", TeleportPermissions.TP_VERTICAL),
                CommandSpec.of(TELEPORT, "bring", TeleportPermissions.TP_USE),
                CommandSpec.of(TELEPORT, "deathback", TeleportPermissions.BACK_USE, "dback"),
                CommandSpec.of(TELEPORT, "descend", TeleportPermissions.TP_VERTICAL),
                CommandSpec.of(TELEPORT, "down", TeleportPermissions.TP_VERTICAL),
                CommandSpec.of(TELEPORT, "goto", TeleportPermissions.TP_USE),
                CommandSpec.of(TELEPORT, "jump", TeleportPermissions.TP_VERTICAL),
                CommandSpec.of(TELEPORT, "mirrorspawn", TeleportPermissions.SPAWN_SET),
                CommandSpec.of(TELEPORT, "removespawn", TeleportPermissions.SPAWN_SET),
                CommandSpec.of(TELEPORT, "rtp", TeleportPermissions.RTP_USE, "wild"),
                CommandSpec.of(TELEPORT, "setmainspawn", TeleportPermissions.SPAWN_SET),
                CommandSpec.of(TELEPORT, "setspawn", TeleportPermissions.SPAWN_SET),
                CommandSpec.of(TELEPORT, "settpr", TeleportPermissions.TELEPORT_SETTPR),
                CommandSpec.of(TELEPORT, "spawn", TeleportPermissions.SPAWN_USE),
                CommandSpec.of(TELEPORT, "thru", TeleportPermissions.TP_VERTICAL),
                CommandSpec.of(TELEPORT, "top", TeleportPermissions.TP_VERTICAL),
                CommandSpec.of(TELEPORT, "tp", TeleportPermissions.TP_USE),
                CommandSpec.of(TELEPORT, "tpa", TeleportPermissions.TPA_USE),
                CommandSpec.of(TELEPORT, "tpaall", TeleportPermissions.TPA_ALL),
                CommandSpec.of(TELEPORT, "tpaccept", TeleportPermissions.TPA_USE, "tpyes"),
                CommandSpec.of(TELEPORT, "tpahere", TeleportPermissions.TPAHERE_USE),
                CommandSpec.of(TELEPORT, "tpalist", TeleportPermissions.TPA_USE, "tprequests"),
                CommandSpec.of(TELEPORT, "tpall", TeleportPermissions.TP_ALL),
                CommandSpec.of(TELEPORT, "tpauto", TeleportPermissions.TPA_AUTO),
                CommandSpec.of(TELEPORT, "tpblock", TeleportPermissions.TPA_BLOCK),
                CommandSpec.of(TELEPORT, "tpcancel", TeleportPermissions.TPA_CANCEL, "tpacancel"),
                CommandSpec.of(TELEPORT, "tpdeny", TeleportPermissions.TPA_USE, "tpno"),
                CommandSpec.of(TELEPORT, "tphere", TeleportPermissions.TP_USE),
                CommandSpec.of(TELEPORT, "tpo", TeleportPermissions.TP_OTHERS),
                CommandSpec.of(TELEPORT, "tpoff", TeleportPermissions.TPA_TOGGLE),
                CommandSpec.of(TELEPORT, "tpoffline", TeleportPermissions.TP_OFFLINE),
                CommandSpec.of(TELEPORT, "tpofflinehere", TeleportPermissions.TP_OFFLINE),
                CommandSpec.of(TELEPORT, "tpohere", TeleportPermissions.TP_OTHERS),
                CommandSpec.of(TELEPORT, "tpon", TeleportPermissions.TPA_TOGGLE),
                CommandSpec.of(TELEPORT, "tppos", TeleportPermissions.TP_POSITION),
                CommandSpec.of(TELEPORT, "tprandomplayer", TeleportPermissions.TP_USE, "tprp"),
                CommandSpec.of(TELEPORT, "tpsettings", TeleportPermissions.TPSETTINGS_USE),
                CommandSpec.of(TELEPORT, "tptoggle", TeleportPermissions.TPA_TOGGLE, "toggletp"),
                CommandSpec.of(TELEPORT, "tpunblock", TeleportPermissions.TPA_BLOCK),
                CommandSpec.of(TELEPORT, "up", TeleportPermissions.TP_VERTICAL));
    }
}

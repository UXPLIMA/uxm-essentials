package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.permission.ContentPermissions;

/** Server content an operator builds: custom commands, menus, guard regions, holograms, NPCs and HUDs. Data, not logic: one row per command, read by {@link CommandSpecCatalog}. */
final class ContentCommandSpecs {

    private static final ModuleId CUSTOMCOMMANDS = ModuleId.of("customcommands");
    private static final ModuleId CUSTOMMENUS = ModuleId.of("custommenus");
    private static final ModuleId GUARD = ModuleId.of("guard");
    private static final ModuleId HOLOGRAMS = ModuleId.of("holograms");
    private static final ModuleId NPC = ModuleId.of("npc");
    private static final ModuleId BOSSBAR = ModuleId.of("bossbar");
    private static final ModuleId SCOREBOARD = ModuleId.of("scoreboard");

    private ContentCommandSpecs() {}

    static List<CommandSpec> all() {
        return List.of(
                CommandSpec.of(CUSTOMCOMMANDS, "customcmd", ContentPermissions.CUSTOMCOMMAND_ADMIN, "customcommand"),
                CommandSpec.of(CUSTOMMENUS, "menu", ContentPermissions.MENU_USE),
                CommandSpec.of(GUARD, "guard", ContentPermissions.GUARD_USE, "bolge"),
                CommandSpec.of(HOLOGRAMS, "hologram", ContentPermissions.HOLOGRAM_USE),
                CommandSpec.of(NPC, "npc", ContentPermissions.NPC_ADMIN),
                CommandSpec.of(BOSSBAR, "bossbars", ContentPermissions.BOSSBAR_USE),
                CommandSpec.of(SCOREBOARD, "scoreboard", ContentPermissions.SCOREBOARD_USE, "sb"));
    }
}

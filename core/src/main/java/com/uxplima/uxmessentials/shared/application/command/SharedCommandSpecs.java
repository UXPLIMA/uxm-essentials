package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.permission.SharedPermissions;

/** The kernel's own commands: the administration root, help, language and backups. Data, not logic: one row per command, read by {@link CommandSpecCatalog}. */
final class SharedCommandSpecs {

    private SharedCommandSpecs() {}

    static List<CommandSpec> all() {
        return List.of(
                CommandSpec.shared("backup", SharedPermissions.ADMIN_BACKUP),
                CommandSpec.shared("help", SharedPermissions.HELP),
                CommandSpec.shared("lang", SharedPermissions.LANG_USE),
                CommandSpec.shared("uxmess", SharedPermissions.ADMIN, "uxmessentials", "uxe"));
    }
}

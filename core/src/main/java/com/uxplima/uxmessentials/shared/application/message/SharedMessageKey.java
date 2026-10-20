package com.uxplima.uxmessentials.shared.application.message;

/**
 * The shared kernel's common message keys: the cross-cutting block no single feature context owns.
 *
 * <p>Several families live here (docs/13-i18n §6, the {@code shared} row): the {@code command.*} failures
 * every command-bearing context raises before it reaches a use case (no permission, player-only,
 * unknown player), the {@code cooldown.*} / {@code warmup.*} feedback the shared {@code Cooldowns} /
 * {@code Warmups} ports render, the {@code lang.*} feedback the {@code /lang} override command emits, and
 * the {@code help.*} lines the cross-cutting {@code /help} listing renders. They sit in {@code shared} so
 * any context references them without a cross-context dependency, exactly as a per-feature
 * {@code MessageKey} enum does for its own block.
 *
 * <p>Like every {@link MessageKey} enum the constant name and the catalog key map 1:1
 * ({@code COMMAND_NO_PERMISSION} ↔ {@code command.no-permission}); the locale-parity guard asserts each
 * has an {@code en} entry and that the {@code command.*} / {@code cooldown.*} / {@code warmup.*} /
 * {@code lang.*} / {@code help.*} / {@code common.*} namespaces are owned here.
 */
public enum SharedMessageKey implements MessageKey {

    // cross-cutting command failures shared by every command-bearing context
    COMMAND_NO_PERMISSION("command.no-permission"),
    COMMAND_PLAYERS_ONLY("command.players-only"),
    COMMAND_UNKNOWN_PLAYER("command.unknown-player"),
    COMMAND_UNKNOWN_WORLD("command.unknown-world"),
    COMMAND_INVALID_POSITION("command.invalid-position"),
    COMMAND_USAGE("command.usage"),
    COMMAND_ERROR("command.error"),

    // shared cooldown / warmup feedback rendered from the Cooldowns / Warmups ports
    COOLDOWN_ACTIVE("cooldown.active"),

    // the /lang personal locale override command
    LANG_STATUS("lang.status"),
    LANG_SET("lang.set"),
    LANG_RESET("lang.reset"),
    LANG_UNKNOWN("lang.unknown"),
    // the language the server forces, which nobody chooses past
    LANG_FORCED("lang.forced"),
    // an operator setting or clearing another player's language, online or away
    LANG_SET_PLAYER("lang.set-player"),
    LANG_SET_PLAYER_AWAY("lang.set-player-away"),
    LANG_RESET_PLAYER("lang.reset-player"),
    LANG_RESET_PLAYER_AWAY("lang.reset-player-away"),
    LANG_FORCED_ANYWAY("lang.forced-anyway"),
    // the code of the language a catalogue is written in, so a name an operator writes per language finds its reader
    LANG_CODE("lang.code"),

    // the cross-cutting /help command listing. One paginated page of usable commands
    HELP_HEADER("help.header"),
    HELP_ENTRY("help.entry"),
    HELP_ENTRY_ALIASES("help.entry-aliases"),
    HELP_EMPTY("help.empty"),
    HELP_NO_MATCH("help.no-match"),
    HELP_FOOTER("help.footer"),
    HELP_FOOTER_PREV("help.footer-prev"),
    HELP_FOOTER_NEXT("help.footer-next"),

    // The /uxmess, /backup and import replies. An operator reads them, and an operator reads their own language too.
    ADMIN_STATUS_HEADER("admin.status.header"),
    ADMIN_STATUS_NONE("admin.status.none"),
    ADMIN_STATUS_ON("admin.status.on"),
    ADMIN_STATUS_OFF("admin.status.off"),
    ADMIN_HELP_HEADER("admin.help.header"),
    ADMIN_HELP_STATUS("admin.help.status"),
    ADMIN_HELP_DOCTOR("admin.help.doctor"),
    ADMIN_HELP_HELP("admin.help.help"),
    ADMIN_HELP_GUI("admin.help.gui"),
    ADMIN_HELP_RELOAD("admin.help.reload"),
    ADMIN_HELP_IMPORT("admin.help.import"),
    ADMIN_HELP_PERMISSIONS("admin.help.permissions"),
    ADMIN_HELP_PLACEHOLDERS("admin.help.placeholders"),
    ADMIN_CHECK_OK("admin.check.ok"),
    ADMIN_CHECK_WARN("admin.check.warn"),
    ADMIN_CHECK_FAIL("admin.check.fail"),
    ADMIN_CHECK_REPAIRED("admin.check.repaired"),
    ADMIN_CHECK_UNCHANGED("admin.check.unchanged"),
    ADMIN_CHECK_RESTART("admin.check.restart"),
    ADMIN_RESULT("admin.result"),
    ADMIN_LINE("admin.line"),
    ADMIN_DOCTOR_HEADER("admin.doctor.header"),
    ADMIN_DOCTOR_RUNNING("admin.doctor.running"),
    ADMIN_DOCTOR_FAILURE("admin.doctor.failure"),
    ADMIN_DOCTOR_BUSY("admin.doctor.busy"),
    ADMIN_CLOSED("admin.closed"),
    ADMIN_REPAIR_HEADER("admin.repair.header"),
    ADMIN_REPAIR_PREVIEW("admin.repair.preview"),
    ADMIN_REPAIR_STARTED("admin.repair.started"),
    ADMIN_RELOAD_HEADER("admin.reload.header"),
    ADMIN_RELOAD_BUSY("admin.reload.busy"),
    ADMIN_RELOAD_STARTED("admin.reload.started"),
    ADMIN_RELOAD_FAILURE("admin.reload.failure"),
    ADMIN_RELOAD_UNKNOWN("admin.reload.unknown"),
    ADMIN_RELOAD_SUMMARY("admin.reload.summary"),
    ADMIN_BACKUP_STARTED("admin.backup.started"),
    ADMIN_BACKUP_DONE("admin.backup.done"),
    ADMIN_BACKUP_SCOPE("admin.backup.scope"),
    ADMIN_BACKUP_FAILED("admin.backup.failed"),
    ADMIN_IMPORT_HEADER("admin.import.header"),
    ADMIN_IMPORT_SOURCE("admin.import.source"),
    ADMIN_IMPORT_USAGE("admin.import.usage"),
    ADMIN_IMPORT_UNKNOWN("admin.import.unknown"),
    ADMIN_IMPORT_STARTED("admin.import.started"),
    ADMIN_IMPORT_STARTED_DRY("admin.import.started-dry"),
    ADMIN_IMPORT_DISABLED("admin.import.disabled"),
    ADMIN_CATALOG_DID_YOU_MEAN("admin.catalog.did-you-mean"),
    ADMIN_CATALOG_WRITE_FAILED("admin.catalog.write-failed"),
    ADMIN_PLACEHOLDERS_HEADER("admin.placeholders.header"),
    ADMIN_PLACEHOLDERS_HINT("admin.placeholders.hint"),
    ADMIN_PLACEHOLDERS_PAGE("admin.placeholders.page"),
    ADMIN_PLACEHOLDERS_NO_AREA("admin.placeholders.no-area"),
    ADMIN_PLACEHOLDERS_EXPORTED("admin.placeholders.exported"),
    ADMIN_PERMISSIONS_HEADER("admin.permissions.header"),
    ADMIN_PERMISSIONS_HINT("admin.permissions.hint"),
    ADMIN_PERMISSIONS_PAGE("admin.permissions.page"),
    ADMIN_PERMISSIONS_NO_AREA("admin.permissions.no-area"),
    ADMIN_PERMISSIONS_EXPORTED("admin.permissions.exported"),

    // What a player is told when another plugin refused an action through the developer API's veto events. One key
    // for every context rather than one per context: this line only has to say that something outside uxmEssentials
    // said no, and the plugin that said no is the one that knows why and can send its own reason.
    COMMON_ACTION_VETOED("common.action-vetoed");

    private final String key;

    SharedMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

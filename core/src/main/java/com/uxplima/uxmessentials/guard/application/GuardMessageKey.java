package com.uxplima.uxmessentials.guard.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The guard context's player facing messages. Each constant maps to a {@code guard.*} key in every
 * {@code messages_<lang>.conf}.
 */
public enum GuardMessageKey implements MessageKey {

    // protection feedback
    DENY("guard.deny"),
    DENY_ENTER("guard.deny.enter"),
    DENY_EXIT("guard.deny.exit"),
    DENY_COMMAND("guard.deny.command"),
    DENY_LOCKED_WORLD("guard.deny.locked-world"),
    BYPASS_REMINDER("guard.bypass.reminder"),
    BYPASS_ON("guard.bypass.on"),
    BYPASS_OFF("guard.bypass.off"),
    BYPASS_NO_PERMISSION("guard.bypass.no-permission"),
    LOCKED_WORLD_STAFF("guard.locked-world.staff"),
    ALERT("guard.alert"),
    INSPECT_ON("guard.inspect.on"),
    INSPECT_OFF("guard.inspect.off"),
    INSPECT_LINE("guard.inspect.line"),

    // command results
    CREATED("guard.created"),
    REDEFINED("guard.redefined"),
    REMOVED("guard.removed"),
    RESTORED("guard.restored"),
    RULE_SET("guard.rule.set"),
    RULE_CLEARED("guard.rule.cleared"),
    ROLE_SET("guard.role.set"),
    ROLE_CLEARED("guard.role.cleared"),
    BEHAVIOUR_SET("guard.behaviour.set"),
    BEHAVIOUR_CLEARED("guard.behaviour.cleared"),
    PRIORITY_SET("guard.priority.set"),
    PARENT_SET("guard.parent.set"),
    PARENT_CLEARED("guard.parent.cleared"),
    FAMILY_SET("guard.family.set"),
    TAG_ADDED("guard.tag.added"),
    TAG_REMOVED("guard.tag.removed"),
    TAG_BULK("guard.tag.bulk"),
    TEMPLATE_SET("guard.template.set"),
    TEMPLATE_CLEARED("guard.template.cleared"),
    TEMPLATE_LIST("guard.template.list"),
    SCOPE_SET("guard.scope.set"),
    LOCK_ON("guard.lock.on"),
    LOCK_OFF("guard.lock.off"),
    UNDONE("guard.undone"),
    RELOADED("guard.reloaded"),

    // errors
    ERROR_UNKNOWN_WORLD("guard.error.unknown-world"),
    ERROR_NO_SUCH_REGION("guard.error.no-such-region"),
    ERROR_ALREADY_EXISTS("guard.error.already-exists"),
    ERROR_INVALID_PARENT("guard.error.invalid-parent"),
    ERROR_REGION_LOCKED("guard.error.region-locked"),
    ERROR_WORLD_LOCKED("guard.error.world-locked"),
    ERROR_UNKNOWN_TEMPLATE("guard.error.unknown-template"),
    ERROR_UNKNOWN_ACTION("guard.error.unknown-action"),
    ERROR_NOTHING_TO_UNDO("guard.error.nothing-to-undo"),
    ERROR_STORE_FAILED("guard.error.store-failed"),
    ERROR_NO_SELECTION("guard.error.no-selection"),
    ERROR_BAD_ID("guard.error.bad-id"),
    ERROR_BAD_TARGET("guard.error.bad-target"),
    ERROR_BAD_VALUE("guard.error.bad-value"),
    ERROR_BAD_SHAPE("guard.error.bad-shape"),
    ERROR_BAD_SCOPE("guard.error.bad-scope"),
    ERROR_PLAYER_ONLY("guard.error.player-only"),
    ERROR_UNKNOWN_PLAYER("guard.error.unknown-player"),
    ERROR_NOT_MANAGER("guard.error.not-manager"),
    ERROR_NO_REGION_HERE("guard.error.no-region-here"),

    // selection
    WAND_GIVEN("guard.wand.given"),
    POS1_SET("guard.pos.first"),
    POS2_SET("guard.pos.second"),
    POINT_ADDED("guard.pos.point"),
    SELECTION_CLEARED("guard.selection.cleared"),
    SELECTION_EXPANDED("guard.selection.expanded"),

    // information
    INFO_HEADER("guard.info.header"),
    INFO_LINE("guard.info.line"),
    INFO_SHAPE("guard.info.shape"),
    INFO_SCOPE("guard.info.scope"),
    INFO_PRIORITY("guard.info.priority"),
    INFO_PARENT("guard.info.parent"),
    INFO_FAMILY("guard.info.family"),
    INFO_TEMPLATE("guard.info.template"),
    INFO_TAGS("guard.info.tags"),
    INFO_LOCKED("guard.info.locked"),
    INFO_RULE("guard.info.rule"),
    LIST_HEADER("guard.list.header"),
    LIST_ENTRY("guard.list.entry"),
    LIST_EMPTY("guard.list.empty"),
    HERE_HEADER("guard.here.header"),
    HERE_ENTRY("guard.here.entry"),
    HERE_NONE("guard.here.none"),
    NEAR_HEADER("guard.near.header"),
    NEAR_ENTRY("guard.near.entry"),
    NEAR_NONE("guard.near.none"),
    WHY_HEADER("guard.why.header"),
    WHY_STEP("guard.why.step"),
    WHY_STEP_INHERITED("guard.why.step-inherited"),
    WHY_STEP_DEFAULTS("guard.why.step-defaults"),
    WHY_STEP_NONE("guard.why.step-none"),
    WHY_RESULT("guard.why.result"),
    WHY_LOCKED_WORLD("guard.why.locked-world"),
    WHY_NOTHING("guard.why.nothing"),
    HISTORY_HEADER("guard.history.header"),
    HISTORY_ENTRY("guard.history.entry"),
    HISTORY_EMPTY("guard.history.empty"),
    LOG_HEADER("guard.log.header"),
    LOG_ENTRY("guard.log.entry"),
    LOG_EMPTY("guard.log.empty"),
    LOG_DISABLED("guard.log.disabled"),
    STATUS_HEADER("guard.status.header"),
    STATUS_LINE("guard.status.line"),
    STATUS_FAMILY("guard.status.family"),
    SHOW_STARTED("guard.show.started"),
    SHOW_STOPPED("guard.show.stopped"),
    ACTIONS_HEADER("guard.actions.header"),
    ACTIONS_ENTRY("guard.actions.entry"),

    // import
    IMPORT_REPORT("guard.import.report"),
    IMPORT_DONE("guard.import.done"),
    IMPORT_UNDONE("guard.import.undone"),
    IMPORT_NOTHING("guard.import.nothing"),
    IMPORT_FAILED("guard.import.failed"),

    // help
    HELP_HEADER("guard.help.header"),
    HELP_LINE("guard.help.line");

    private final String key;

    GuardMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

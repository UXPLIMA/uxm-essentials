package com.uxplima.uxmessentials.customcommands.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The customcommands context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code CUSTOMCOMMAND_ON_COOLDOWN} to {@code customcommand.on-cooldown}); the
 * constant is the compile-time handle, the catalog holds the text.
 *
 * <p>Operator-authored text is not translated and never passes through here: a command's own {@code deny-message}
 * and the body of a {@code message:} action are data read from a command file and shown verbatim, exactly as menu
 * files already behave.
 *
 * <p>Per the i18n contract a disabled module still ships its keys, so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set whether or not customcommands is enabled.
 */
public enum CustomCommandsMessageKey implements MessageKey {

    /** Gate 1: a console ran a command whose file sets {@code console = false}. */
    CUSTOMCOMMAND_CONSOLE_DENIED("customcommand.console-denied"),

    /** Gate 2: the sender lacks the command's own permission and the file names no deny message. */
    CUSTOMCOMMAND_NO_PERMISSION("customcommand.no-permission"),

    /** Gate 4: the chain re-entered itself more times than the module allows. */
    CUSTOMCOMMAND_DEPTH_EXCEEDED("customcommand.depth-exceeded"),

    /** Gate 5: the requirements failed and the file names no deny chain. */
    CUSTOMCOMMAND_REQUIREMENTS_FAILED("customcommand.requirements-failed"),

    /** Gate 6: the command is still cooling down; {@code {time}} is the remaining wait. */
    CUSTOMCOMMAND_ON_COOLDOWN("customcommand.on-cooldown"),

    /** Gate 7: the warmup started; {@code {time}} is how long the player must stand still. */
    CUSTOMCOMMAND_WARMUP_STARTED("customcommand.warmup-started"),

    /** Gate 7: the player moved, so the warmup was cancelled. */
    CUSTOMCOMMAND_WARMUP_CANCELLED("customcommand.warmup-cancelled"),

    /** Gate 8: the player cannot pay; {@code {cost}} is the formatted price. */
    CUSTOMCOMMAND_CANNOT_AFFORD("customcommand.cannot-afford"),

    /** Header for the command listing; {@code {count}} is how many definitions loaded. */
    CUSTOMCOMMAND_LIST_HEADER("customcommand.list.header"),

    /** One row of the command listing: {@code {id}}, {@code {name}} and {@code {aliases}}. */
    CUSTOMCOMMAND_LIST_ENTRY("customcommand.list.entry"),

    /** Reply for the command listing when no definition loaded. */
    CUSTOMCOMMAND_LIST_EMPTY("customcommand.list.empty"),

    /** One warning row of the command listing: {@code {warning}}. */
    CUSTOMCOMMAND_LIST_WARNING("customcommand.list.warning"),

    /** Reply when a subcommand names an id nothing loaded: {@code {id}}. */
    CUSTOMCOMMAND_NOT_FOUND("customcommand.not-found"),

    /** Header of the definition summary: {@code {id}}, {@code {name}} and {@code {aliases}}. */
    CUSTOMCOMMAND_INFO_HEADER("customcommand.info.header"),

    /** The gate line of the summary: {@code {permission}}, {@code {console}}, {@code {cooldown}}, {@code {warmup}}, {@code {cost}}. */
    CUSTOMCOMMAND_INFO_GATES("customcommand.info.gates"),

    /** One argument row of the summary: {@code {name}}, {@code {type}} and {@code {required}}. */
    CUSTOMCOMMAND_INFO_ARGUMENT("customcommand.info.argument"),

    /** The chain line of the summary: the {@code {requirements}} and {@code {actions}} counts. */
    CUSTOMCOMMAND_INFO_CHAIN("customcommand.info.chain"),

    /** Reply after a full reload: {@code {loaded}} and {@code {skipped}}. */
    CUSTOMCOMMAND_RELOADED("customcommand.reloaded"),

    /** Reply after a single-file reload naming {@code {id}}. */
    CUSTOMCOMMAND_RELOADED_ONE("customcommand.reloaded-one"),

    /** Confirmation that a chain was dispatched: {@code {id}} for {@code {player}}. */
    CUSTOMCOMMAND_RUN_DISPATCHED("customcommand.run.dispatched"),

    /** Reply when the sender may not run a chain for somebody else. */
    CUSTOMCOMMAND_RUN_OTHERS_DENIED("customcommand.run.others-denied"),

    /** Reply for a dry run when every gate would open. */
    CUSTOMCOMMAND_TEST_PASSED("customcommand.test.passed"),

    /** Reply for a dry run naming the first gate that would stop the sender: {@code {gate}}. */
    CUSTOMCOMMAND_TEST_BLOCKED("customcommand.test.blocked"),

    /** Confirmation that a definition file was deleted: {@code {id}}. */
    CUSTOMCOMMAND_DELETED("customcommand.deleted"),

    /** Prompt asking the sender to confirm a delete: {@code {id}}. */
    CUSTOMCOMMAND_DELETE_CONFIRM("customcommand.delete-confirm"),

    /** Reply when a write under the definitions folder failed; the cause is in the server log. */
    CUSTOMCOMMAND_WRITE_FAILED("customcommand.write-failed"),

    /** Reply when a new definition is asked for under an id that already exists: {@code {id}}. */
    CUSTOMCOMMAND_ALREADY_EXISTS("customcommand.already-exists"),

    /** Reply when a value typed into the wizard is not valid for the step that asked for it. */
    CUSTOMCOMMAND_WIZARD_INVALID("customcommand.wizard.invalid"),

    /** Opening line of the wizard, naming how to cancel. */
    CUSTOMCOMMAND_WIZARD_START("customcommand.wizard.start"),

    /** Wizard prompt for the command word. */
    CUSTOMCOMMAND_WIZARD_NAME("customcommand.wizard.name"),

    /** Wizard prompt for the aliases. */
    CUSTOMCOMMAND_WIZARD_ALIASES("customcommand.wizard.aliases"),

    /** Wizard prompt for the aliases that belong to one language only. */
    CUSTOMCOMMAND_WIZARD_LOCALIZED("customcommand.wizard.localized"),

    /** Wizard prompt for the permission node, showing the suggested default: {@code {suggested}}. */
    CUSTOMCOMMAND_WIZARD_PERMISSION("customcommand.wizard.permission"),

    /** Wizard prompt asking whether the console may run the command. */
    CUSTOMCOMMAND_WIZARD_CONSOLE("customcommand.wizard.console"),

    /** Wizard prompt for one argument, repeated until the operator is done. */
    CUSTOMCOMMAND_WIZARD_ARGUMENT("customcommand.wizard.argument"),

    /** Wizard prompt for one action, repeated until the operator is done. */
    CUSTOMCOMMAND_WIZARD_ACTION("customcommand.wizard.action"),

    /** Wizard preview header shown before the save question: {@code {id}}. */
    CUSTOMCOMMAND_WIZARD_PREVIEW("customcommand.wizard.preview"),

    /** Wizard save confirmation: {@code {id}}. */
    CUSTOMCOMMAND_WIZARD_SAVED("customcommand.wizard.saved"),

    /** Wizard cancellation acknowledgement. */
    CUSTOMCOMMAND_WIZARD_CANCELLED("customcommand.wizard.cancelled"),

    /** The note every create, rename and delete carries: the command word itself needs a restart. */
    CUSTOMCOMMAND_RESTART_REQUIRED("customcommand.restart-required"),

    /** Usage line for the admin command when it is run with no subcommand. */
    CUSTOMCOMMAND_USAGE("customcommand.usage");

    private final String key;

    CustomCommandsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

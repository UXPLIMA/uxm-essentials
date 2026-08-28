package com.uxplima.uxmessentials.customcommands.adapter.inbound.command;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.customcommands.adapter.CustomCommandWriter;
import com.uxplima.uxmessentials.customcommands.application.CustomCommandsMessageKey;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ArgumentKind;
import com.uxplima.uxmessentials.customcommands.domain.CommandArgument;
import com.uxplima.uxmessentials.customcommands.domain.CommandLiteral;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommandId;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The chat wizard behind {@code /customcmd create <id>}: one question at a time, in the order the design fixes,
 * ending in a file written through {@link CustomCommandWriter} so what the wizard produces is exactly what the
 * loader reads back.
 *
 * <p>Every step re-asks on an answer it cannot use rather than aborting, because losing six answers to one typo is
 * the worst thing a wizard can do. {@code cancel} ends it at any point and writes nothing; the repeated steps
 * (arguments, actions) end on {@code done}. The id is claimed before the first question, so a wizard that could
 * never save is refused up front rather than at the end.
 *
 * <p>The command word a wizard creates becomes typeable after the next restart, which the save step says plainly.
 * Nothing here tries to work around that: Brigadier accepts registrations only while the server starts, and
 * {@code /customcmd run} covers the gap.
 */
@NullMarked
public final class CreateWizard {

    /** The answer that ends a repeated step. */
    private static final String DONE = "done";

    /** The answer that means "leave this empty". */
    private static final String NONE = "none";

    /** The answer that accepts the suggested permission node. */
    private static final String KEEP = "keep";

    /** The answer that writes the file. */
    private static final String SAVE = "save";

    /** The prefix every suggested permission node is built from. */
    private static final String NODE_PREFIX = "uxmessentials.customcommand.";

    /** Answers that list several values are separated by runs of whitespace. */
    private static final Splitter WHITESPACE = Splitter.on(Pattern.compile("\\s+"));

    private final WizardPrompt prompt;
    private final Path directory;
    private final Supplier<Set<String>> takenIds;
    private final CommandFeedback feedback;
    private final Consumer<String> onSaved;
    private final Logger log;

    public CreateWizard(
            WizardPrompt prompt,
            Path directory,
            Supplier<Set<String>> takenIds,
            CommandFeedback feedback,
            Consumer<String> onSaved,
            Logger log) {
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.takenIds = Objects.requireNonNull(takenIds, "takenIds");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.onSaved = Objects.requireNonNull(onSaved, "onSaved");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Start the wizard for {@code player} under {@code rawId}. Returns false when the id cannot be used, in which
     * case nothing was asked and the player has already been told why.
     */
    public boolean start(Player player, PlayerRef viewer, String rawId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(rawId, "rawId");
        if (!CustomCommandId.valid(rawId)) {
            feedback.send(player, CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_INVALID);
            return false;
        }
        CustomCommandId id = CustomCommandId.of(rawId);
        if (takenIds.get().contains(id.value())) {
            feedback.send(player, CustomCommandsMessageKey.CUSTOMCOMMAND_ALREADY_EXISTS, Map.of("id", id.value()));
            return false;
        }
        feedback.send(player, CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_START);
        askName(new Draft(player, viewer, id));
        return true;
    }

    private void askName(Draft draft) {
        ask(
                draft,
                "customcommand.wizard.name",
                CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_NAME,
                Map.of(),
                answer -> {
                    String word = answer.strip().toLowerCase(Locale.ROOT);
                    if (!CommandLiteral.validWord(word)) {
                        retry(draft, this::askName);
                        return;
                    }
                    draft.name = word;
                    askAliases(draft);
                });
    }

    private void askAliases(Draft draft) {
        ask(
                draft,
                "customcommand.wizard.aliases",
                CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_ALIASES,
                Map.of(),
                answer -> {
                    if (!isNone(answer)) {
                        for (String word : WHITESPACE.splitToList(answer.strip())) {
                            String normalised = word.toLowerCase(Locale.ROOT);
                            if (CommandLiteral.validWord(normalised)) {
                                draft.aliases.add(normalised);
                            }
                        }
                    }
                    askLocalized(draft);
                });
    }

    private void askLocalized(Draft draft) {
        ask(
                draft,
                "customcommand.wizard.localized",
                CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_LOCALIZED,
                Map.of(),
                answer -> {
                    if (!isNone(answer)) {
                        for (String pair : WHITESPACE.splitToList(answer.strip())) {
                            int colon = pair.indexOf(':');
                            if (colon <= 0 || colon == pair.length() - 1) {
                                continue;
                            }
                            String locale = pair.substring(0, colon).toLowerCase(Locale.ROOT);
                            String word = pair.substring(colon + 1).toLowerCase(Locale.ROOT);
                            if (CommandLiteral.validWord(word)) {
                                draft.localized
                                        .computeIfAbsent(locale, key -> new ArrayList<>())
                                        .add(word);
                            }
                        }
                    }
                    askPermission(draft);
                });
    }

    private void askPermission(Draft draft) {
        String suggested = NODE_PREFIX + draft.id.value();
        ask(
                draft,
                "customcommand.wizard.permission",
                CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_PERMISSION,
                Map.of("suggested", suggested),
                answer -> {
                    String typed = answer.strip();
                    if (KEEP.equalsIgnoreCase(typed)) {
                        draft.permission = Optional.of(suggested);
                    } else if (!isNone(typed)) {
                        draft.permission = Optional.of(typed);
                    }
                    askConsole(draft);
                });
    }

    private void askConsole(Draft draft) {
        ask(
                draft,
                "customcommand.wizard.console",
                CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_CONSOLE,
                Map.of(),
                answer -> {
                    Optional<Boolean> yesNo = yesNo(answer);
                    if (yesNo.isEmpty()) {
                        retry(draft, this::askConsole);
                        return;
                    }
                    draft.console = yesNo.get();
                    askArgument(draft);
                });
    }

    private void askArgument(Draft draft) {
        ask(
                draft,
                "customcommand.wizard.argument",
                CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_ARGUMENT,
                Map.of(),
                answer -> {
                    if (isDone(answer)) {
                        askAction(draft);
                        return;
                    }
                    Optional<CommandArgument> argument = parseArgument(answer);
                    if (argument.isEmpty()) {
                        retry(draft, this::askArgument);
                        return;
                    }
                    draft.arguments.add(argument.get());
                    askArgument(draft);
                });
    }

    private void askAction(Draft draft) {
        ask(
                draft,
                "customcommand.wizard.action",
                CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_ACTION,
                Map.of(),
                answer -> {
                    if (isDone(answer)) {
                        if (draft.actions.isEmpty()) {
                            retry(draft, this::askAction);
                            return;
                        }
                        preview(draft);
                        return;
                    }
                    draft.actions.add(answer.strip());
                    askAction(draft);
                });
    }

    private void preview(Draft draft) {
        ask(
                draft,
                "customcommand.wizard.preview",
                CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_PREVIEW,
                Map.of("id", draft.id.value()),
                answer -> {
                    if (!SAVE.equalsIgnoreCase(answer.strip())) {
                        cancel(draft);
                        return;
                    }
                    save(draft);
                });
    }

    private void save(Draft draft) {
        CustomCommand command = draft.build();
        try {
            CustomCommandWriter.write(directory, command);
        } catch (IOException failure) {
            log.warn("could not write the definition '{}': {}", draft.id.value(), String.valueOf(failure.getMessage()));
            feedback.send(draft.player, CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_INVALID);
            return;
        }
        onSaved.accept(draft.id.value());
        feedback.send(
                draft.player, CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_SAVED, Map.of("id", draft.id.value()));
        feedback.send(draft.player, CustomCommandsMessageKey.CUSTOMCOMMAND_RESTART_REQUIRED);
    }

    private void cancel(Draft draft) {
        feedback.send(draft.player, CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_CANCELLED);
    }

    /** Say the answer was unusable and put the same question again, so one typo costs one question. */
    private void retry(Draft draft, Consumer<Draft> step) {
        feedback.send(draft.player, CustomCommandsMessageKey.CUSTOMCOMMAND_WIZARD_INVALID);
        step.accept(draft);
    }

    private void ask(
            Draft draft, String key, MessageKey label, Map<String, String> placeholders, Consumer<String> onAnswer) {
        prompt.ask(
                draft.player,
                draft.viewer,
                new InputRequest(key, label, placeholders, null),
                onAnswer,
                () -> cancel(draft));
    }

    /** One argument row written as {@code name type [min] [max]}, or empty when it is not usable. */
    private static Optional<CommandArgument> parseArgument(String answer) {
        List<String> parts = WHITESPACE.splitToList(answer.strip());
        if (parts.isEmpty() || parts.get(0).isBlank()) {
            return Optional.empty();
        }
        String name = parts.get(0);
        String typeToken = parts.size() > 1 ? parts.get(1) : "string";
        Optional<ArgumentKind> kind = ArgumentKind.parse(typeToken);
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        boolean optional = parts.stream().skip(2).anyMatch("optional"::equalsIgnoreCase);
        return Optional.of(new CommandArgument(
                name, kind.get(), optional, ArgumentKind.isRestToken(typeToken), Optional.empty(), Optional.empty()));
    }

    private static Optional<Boolean> yesNo(String answer) {
        return switch (answer.strip().toLowerCase(Locale.ROOT)) {
            case "yes", "y", "true" -> Optional.of(true);
            case "no", "n", "false" -> Optional.of(false);
            default -> Optional.empty();
        };
    }

    private static boolean isNone(String answer) {
        return NONE.equalsIgnoreCase(answer.strip()) || answer.isBlank();
    }

    private static boolean isDone(String answer) {
        return DONE.equalsIgnoreCase(answer.strip());
    }

    /** The answers gathered so far. It lives for one wizard run on one player's thread, so it is a plain object. */
    private static final class Draft {

        private final Player player;
        private final PlayerRef viewer;
        private final CustomCommandId id;
        private final List<String> aliases = new ArrayList<>();
        private final Map<String, List<String>> localized = new LinkedHashMap<>();
        private final List<CommandArgument> arguments = new ArrayList<>();
        private final List<String> actions = new ArrayList<>();
        private String name = "";
        private Optional<String> permission = Optional.empty();
        private boolean console = true;

        Draft(Player player, PlayerRef viewer, CustomCommandId id) {
            this.player = player;
            this.viewer = viewer;
            this.id = id;
            this.name = id.value();
        }

        CustomCommand build() {
            return new CustomCommand(
                    id,
                    new CommandLiteral(name, List.copyOf(aliases), Map.copyOf(localized)),
                    permission,
                    Optional.empty(),
                    console,
                    id.value(),
                    Optional.empty(),
                    Duration.ZERO,
                    Duration.ZERO,
                    0,
                    List.copyOf(arguments),
                    List.of(),
                    ActionChain.empty(),
                    ActionChain.of(List.copyOf(actions), ActionChain.ChainLimits.defaults()));
        }
    }
}

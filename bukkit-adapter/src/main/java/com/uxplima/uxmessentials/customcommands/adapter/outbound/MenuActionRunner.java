package com.uxplima.uxmessentials.customcommands.adapter.outbound;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.customcommands.application.port.ActionRunner;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ActionStep;
import com.uxplima.uxmessentials.customcommands.domain.ChainArguments;
import com.uxplima.uxmessentials.customcommands.domain.CommandLineArguments;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.CommandActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.LivePlayers;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickCommandRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.TypedText;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.runtime.ActionArguments;
import com.uxplima.uxmlib.menu.spec.Ref;

/**
 * Runs a custom command's action chain through the menu engine's action vocabulary, so a command does exactly what
 * a menu item does and nothing new had to be invented for it. Argument tokens are expanded before dispatch, a step
 * that carries an offset is scheduled instead of run inline, and the privileged heads are gated by the module's own
 * policy rather than by whoever wrote the file.
 *
 * <p>The privileged heads ({@code console}, {@code console-random}, {@code command-as-op}, {@code command-op}) are
 * carried out here rather than by the menu engine, whose own handlers answer to {@code custommenus} settings written
 * for menu files. So {@code allow-console-actions} and {@code allow-op-actions} are the only switches a custom command
 * answers to.
 *
 * <p>A console run has no live player behind it, and the engine's vocabulary is written against one. Rather than
 * let every step quietly evaporate, a chain run for a system actor takes a narrow second path that handles the
 * console-safe heads ({@code console}, {@code console-random}, {@code broadcast}, {@code message}) and reports each
 * step it had to
 * skip by name, so an operator who marked a command {@code console = true} can see which of its steps needed a
 * player.
 */
public final class MenuActionRunner implements ActionRunner {

    /** The action head that runs its value as the console. */
    private static final String CONSOLE_HEAD = "console";

    /** The action head that runs one command, picked at random from a {@code ;} list, as the console. */
    private static final String CONSOLE_RANDOM_HEAD = "console-random";

    /** The heads that run a command as the console. */
    private static final Set<String> CONSOLE_HEADS = Set.of(CONSOLE_HEAD, CONSOLE_RANDOM_HEAD);

    /** The heads that run their value as the player with operator rights briefly granted. */
    private static final Set<String> OP_HEADS = Set.of("command-as-op", "command-op");

    /** The action head that broadcasts its value to the whole server. */
    private static final String BROADCAST_HEAD = "broadcast";

    /** The action head that sends its value to the actor. */
    private static final String MESSAGE_HEAD = "message";

    /** The heads that still mean something when the actor is the console rather than a player. */
    private static final Set<String> CONSOLE_SAFE_HEADS =
            Set.of(CONSOLE_HEAD, CONSOLE_RANDOM_HEAD, BROADCAST_HEAD, MESSAGE_HEAD);

    /** An argument token: {@code %args%} or {@code %arg_<name>%}; {@code group(1)} is the name, absent for the first. */
    private static final Pattern ARGUMENT = Pattern.compile("%(?:args|arg_([^%\\s]+))%");

    /**
     * The heads whose value is parsed as MiniMessage and shown to players. An argument the player typed goes into one
     * as text, so a tag in it reads as the words: typed into a {@code broadcast}, a click tag would otherwise reach
     * every player on the server.
     */
    private static final Set<String> STYLED_TEXT_HEADS =
            Set.of(BROADCAST_HEAD, MESSAGE_HEAD, "message-to", "whisper", "action-bar", "actionbar", "title", "toast");

    /** The head whose value is a JSON text component, where an argument goes into a JSON string as its text. */
    private static final String JSON_HEAD = "broadcast-json";

    /** The head whose value is a legacy line, where {@code &} and a code character change the colour. */
    private static final String LEGACY_HEAD = "broadcast-legacy";

    /** The key a step's value is resolved under when the engine's argument tokens are filled here. */
    private static final String VALUE = "value";

    private final Menus menus;
    private final ClickCommandRunner commands;
    private final Scheduler scheduler;
    private final Logger log;
    private final Supplier<PrivilegedActions> policy;

    public MenuActionRunner(
            Menus menus,
            ClickCommandRunner commands,
            Scheduler scheduler,
            Logger log,
            Supplier<PrivilegedActions> policy) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * What the module lets a chain do beyond its own actor: run something as the console, run something with
     * operator rights, and whether either should be written to the log when it happens.
     */
    public record PrivilegedActions(boolean console, boolean op, boolean audit) {

        /** The shipped policy: the console head is on, the operator head is off, both are audited when used. */
        public static PrivilegedActions defaults() {
            return new PrivilegedActions(true, false, true);
        }
    }

    @Override
    public void run(PlayerRef actor, ActionChain chain, ChainArguments arguments) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(arguments, "arguments");
        PrivilegedActions allowed = policy.get();
        if (actor.isSystem()) {
            runAsSystem(actor, chain, arguments, allowed);
            return;
        }
        for (ActionStep step : chain.steps()) {
            Map<String, String> source = sourceFor(head(step.token()), arguments);
            String token = withoutLeadingSpace(expand(step.token(), source));
            // The engine fills %argument_<name>% and %<name>% in the step from the values it is handed, so it is
            // handed them made ready for this step exactly as expand made its own.
            Map<String, String> values = forStep(head(step.token()), source);
            if (!permitted(token, allowed)) {
                // A chain that names a privileged action the operator turned off skips that step and says which
                // head it lost, rather than quietly granting the privilege or dropping the whole chain.
                log.warn(
                        "skipping the '{}' action for {}: it is disabled in modules/customcommands/config.conf",
                        head(token),
                        actor.name());
                continue;
            }
            audit(token, actor, allowed);
            String head = head(token);
            if (CONSOLE_HEADS.contains(head) || OP_HEADS.contains(head)) {
                runPrivileged(actor, head, withEngineTokens(value(token), values), step.offset());
                continue;
            }
            Ref ref = Ref.parse(token);
            // The engine spends a live player, so an action for somebody who has left is dropped rather than run
            // against a name, the same as the engine does when its own lookup misses.
            if (step.offset().isZero()) {
                LivePlayers.of(actor).ifPresent(live -> menus.execute(live, ref, values));
            } else {
                scheduler.asyncAfter(
                        step.offset(), () -> LivePlayers.of(actor).ifPresent(live -> menus.execute(live, ref, values)));
            }
        }
    }

    /**
     * Run {@code chain} for a non-player actor. Only the heads that mean something without a player are carried out;
     * anything else is named in the log and skipped, because a console run of a chain written for players should
     * read as a partial run rather than as a failure or as a success that did nothing.
     */
    private void runAsSystem(PlayerRef actor, ActionChain chain, ChainArguments arguments, PrivilegedActions allowed) {
        for (ActionStep step : chain.steps()) {
            String token = expand(step.token(), sourceFor(head(step.token()), arguments));
            String head = head(token);
            if (!permitted(token, allowed)) {
                log.warn(
                        "skipping the '{}' action for {}: it is disabled in modules/customcommands/config.conf",
                        head,
                        actor.name());
                continue;
            }
            if (!CONSOLE_SAFE_HEADS.contains(head)) {
                log.info("skipping the '{}' action of a console run: it needs a player", head);
                continue;
            }
            audit(token, actor, allowed);
            Runnable effect = () -> dispatchAsSystem(head, value(token));
            if (step.offset().isZero()) {
                scheduler.onGlobal(effect);
            } else {
                scheduler.asyncAfter(step.offset(), () -> scheduler.onGlobal(effect));
            }
        }
    }

    /**
     * {@code value} with the engine's own {@code %argument_<name>%} tokens filled from {@code values}, as the engine
     * fills them in a step it runs, so a step written either way behaves the same when it is carried out here.
     */
    private static String withEngineTokens(String value, Map<String, String> values) {
        return ActionArguments.resolve(Map.of(VALUE, value), values).getOrDefault(VALUE, value);
    }

    /**
     * Carry out a console or operator step for a player. A step due now is queued on the player's thread, where the
     * engine runs the other steps, so the chain keeps its order; the console part then runs on the global thread. A
     * later step runs on the thread its effect needs when it is due.
     */
    private void runPrivileged(PlayerRef actor, String head, String value, Duration offset) {
        if (CONSOLE_HEADS.contains(head)) {
            Runnable console = () -> onGlobalThread(() -> runAsConsole(head, value));
            if (offset.isZero()) {
                scheduler.onEntity(actor, console);
            } else {
                scheduler.asyncAfter(offset, () -> scheduler.onGlobal(() -> runAsConsole(head, value)));
            }
            return;
        }
        Runnable asOp = () -> LivePlayers.of(actor).ifPresent(live -> runAsOp(live, value));
        if (offset.isZero()) {
            scheduler.onEntity(actor, asOp);
        } else {
            scheduler.laterEntity(actor, offset, asOp);
        }
    }

    /** Run {@code task} now when this is the global thread, as it is on a server without regions, else there. */
    private void onGlobalThread(Runnable task) {
        if (scheduler.onGlobalThread()) {
            task.run();
        } else {
            scheduler.onGlobal(task);
        }
    }

    /** Run {@code value}, or for {@code console-random} one command picked from it, as the console. */
    private void runAsConsole(String head, String value) {
        String command = CONSOLE_RANDOM_HEAD.equals(head) ? CommandActions.pickRandom(value) : value;
        String bare = CommandActions.stripSlash(command);
        if (!bare.isEmpty()) {
            commands.runAsConsole(bare);
        }
    }

    /** Run {@code value} as {@code player} with operator rights for the one command. */
    private void runAsOp(Player player, String value) {
        String bare = CommandActions.stripSlash(value);
        if (!bare.isEmpty()) {
            commands.runAsPlayerOp(player, bare);
        }
    }

    /** Carry out one console-safe step on the global thread; a head outside the four never reaches here. */
    private void dispatchAsSystem(String head, String value) {
        if (value.isBlank()) {
            return;
        }
        switch (head) {
            case CONSOLE_HEAD, CONSOLE_RANDOM_HEAD -> runAsConsole(head, value);
            case BROADCAST_HEAD -> Bukkit.broadcast(StyledText.render(value));
            case MESSAGE_HEAD -> Bukkit.getConsoleSender().sendMessage(StyledText.render(value));
            default -> {
                // Unreachable: the caller filters on CONSOLE_SAFE_HEADS before scheduling anything.
            }
        }
    }

    /**
     * {@code token} with the spaces after its colon removed: {@code console: say hi} is written for readability, and the
     * engine hands an action everything after the colon, so the console would otherwise be asked to run {@code " say"}.
     */
    private static String withoutLeadingSpace(String token) {
        int colon = token.indexOf(':');
        return colon < 0
                ? token
                : token.substring(0, colon + 1) + token.substring(colon + 1).stripLeading();
    }

    /** The value of an action token: everything after the first colon, or the empty string when it carries none. */
    private static String value(String token) {
        int colon = token.indexOf(':');
        return colon < 0 ? "" : token.substring(colon + 1).strip();
    }

    /**
     * Expand the argument tokens a definition may write inside an action: {@code %arg_<name>%} for each declared
     * argument and {@code %args%} for the raw remainder. The map is handed to the engine unchanged as well, so the
     * engine's own {@code %argument_<name>%} expansion still works and a file written either way behaves the same.
     *
     * <p>One pass: an argument that itself holds {@code %args%} or another argument's token is put in as typed. An
     * argument going into text players read is put in as text ({@link #STYLED_TEXT_HEADS}, {@link #JSON_HEAD}); one
     * going into a command is put in as typed, since a command takes its words exactly, and comes from the values
     * {@link #sourceFor} allows there.
     */
    private static String expand(String token, Map<String, String> arguments) {
        if (token.indexOf('%') < 0) {
            return token;
        }
        String head = head(token);
        Matcher matcher = ARGUMENT.matcher(token);
        StringBuilder out = new StringBuilder(token.length() + 16);
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = name == null ? arguments.getOrDefault("args", "") : arguments.get(name);
            String inserted = value == null ? matcher.group() : asTextFor(head, value);
            matcher.appendReplacement(out, Matcher.quoteReplacement(inserted));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * The values a step with this {@code head} may use. An action that acts on its value gets only those that passed
     * the command's argument rules, so neither expand nor the engine can put any other typed value into it.
     */
    private static Map<String, String> sourceFor(String head, ChainArguments arguments) {
        return CommandLineArguments.lineOf(head).map(arguments::forLine).orElseGet(arguments::typed);
    }

    /** Every value of {@code arguments} as it goes into an action with this {@code head}. */
    private static Map<String, String> forStep(String head, Map<String, String> arguments) {
        Map<String, String> ready = new LinkedHashMap<>();
        arguments.forEach((name, value) -> ready.put(name, asTextFor(head, value)));
        return ready;
    }

    /** {@code value} as it goes into an action with this {@code head}: as words where the action shows text. */
    private static String asTextFor(String head, String value) {
        if (STYLED_TEXT_HEADS.contains(head)) {
            return StyledText.escape(value);
        }
        if (JSON_HEAD.equals(head)) {
            return jsonStringContent(value);
        }
        if (LEGACY_HEAD.equals(head)) {
            return TypedText.withoutLegacyCodes(value);
        }
        return value;
    }

    /** {@code value} as the inside of a JSON string, so it cannot close the string and add keys of its own. */
    private static String jsonStringContent(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                // Gson also reads a string written in single quotes, so a quote of either kind is escaped.
                case '\'' -> escaped.append("\\u0027");
                case '\\' -> escaped.append("\\\\");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /** Whether the module's policy allows this action head; every head but the two privileged ones is allowed. */
    private static boolean permitted(String token, PrivilegedActions allowed) {
        String head = head(token);
        if (CONSOLE_HEADS.contains(head)) {
            return allowed.console();
        }
        if (OP_HEADS.contains(head)) {
            return allowed.op();
        }
        return true;
    }

    /** Write one line naming who triggered a privileged action, when the policy asks for the audit trail. */
    private void audit(String token, PlayerRef actor, PrivilegedActions allowed) {
        String head = head(token);
        if (!allowed.audit() || (!CONSOLE_HEADS.contains(head) && !OP_HEADS.contains(head))) {
            return;
        }
        log.info("custom command action '{}' run for {}", head, actor.name());
    }

    /** The head of an action token: everything before the first colon, lowercased. */
    private static String head(String token) {
        int colon = token.indexOf(':');
        String head = colon < 0 ? token : token.substring(0, colon);
        return head.strip().toLowerCase(Locale.ROOT);
    }
}

package com.uxplima.uxmessentials.customcommands.adapter.outbound;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.bukkit.Bukkit;

import com.uxplima.uxmessentials.customcommands.application.port.ActionRunner;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ActionStep;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Runs a custom command's action chain through the menu engine's action vocabulary, so a command does exactly what
 * a menu item does and nothing new had to be invented for it. Argument tokens are expanded before dispatch, a step
 * that carries an offset is scheduled instead of run inline, and the two privileged heads are gated by the module's
 * own policy rather than by whoever wrote the file.
 *
 * <p>A console run has no live player behind it, and the engine's vocabulary is written against one. Rather than
 * let every step quietly evaporate, a chain run for a system actor takes a narrow second path that handles the
 * three console-safe heads ({@code console}, {@code broadcast}, {@code message}) and reports each step it had to
 * skip by name, so an operator who marked a command {@code console = true} can see which of its steps needed a
 * player.
 */
public final class MenuActionRunner implements ActionRunner {

    /** The action head that runs its value as the console. */
    private static final String CONSOLE_HEAD = "console";

    /** The action head that runs its value as the player with operator rights briefly granted. */
    private static final String OP_HEAD = "command-as-op";

    /** The action head that broadcasts its value to the whole server. */
    private static final String BROADCAST_HEAD = "broadcast";

    /** The action head that sends its value to the actor. */
    private static final String MESSAGE_HEAD = "message";

    /** The heads that still mean something when the actor is the console rather than a player. */
    private static final Set<String> CONSOLE_SAFE_HEADS = Set.of(CONSOLE_HEAD, BROADCAST_HEAD, MESSAGE_HEAD);

    /** The token carrying the raw remaining input a command was invoked with. */
    private static final String RAW_ARGUMENTS = "%args%";

    private final Menus menus;
    private final Scheduler scheduler;
    private final Logger log;
    private final Supplier<PrivilegedActions> policy;

    public MenuActionRunner(Menus menus, Scheduler scheduler, Logger log, Supplier<PrivilegedActions> policy) {
        this.menus = Objects.requireNonNull(menus, "menus");
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
    public void run(PlayerRef actor, ActionChain chain, Map<String, String> arguments) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(arguments, "arguments");
        PrivilegedActions allowed = policy.get();
        if (actor.isSystem()) {
            runAsSystem(actor, chain, arguments, allowed);
            return;
        }
        for (ActionStep step : chain.steps()) {
            String token = expand(step.token(), arguments);
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
            Ref ref = Ref.parse(token);
            if (step.offset().isZero()) {
                menus.execute(actor, ref, arguments);
            } else {
                scheduler.asyncAfter(step.offset(), () -> menus.execute(actor, ref, arguments));
            }
        }
    }

    /**
     * Run {@code chain} for a non-player actor. Only the heads that mean something without a player are carried out;
     * anything else is named in the log and skipped, because a console run of a chain written for players should
     * read as a partial run rather than as a failure or as a success that did nothing.
     */
    private void runAsSystem(
            PlayerRef actor, ActionChain chain, Map<String, String> arguments, PrivilegedActions allowed) {
        for (ActionStep step : chain.steps()) {
            String token = expand(step.token(), arguments);
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

    /** Carry out one console-safe step on the global thread; a head outside the three never reaches here. */
    private void dispatchAsSystem(String head, String value) {
        if (value.isBlank()) {
            return;
        }
        switch (head) {
            case CONSOLE_HEAD -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(value));
            case BROADCAST_HEAD -> Bukkit.broadcast(StyledText.render(value));
            case MESSAGE_HEAD -> Bukkit.getConsoleSender().sendMessage(StyledText.render(value));
            default -> {
                // Unreachable: the caller filters on CONSOLE_SAFE_HEADS before scheduling anything.
            }
        }
    }

    /** A command action's value with a single leading slash removed, which is what the dispatcher expects. */
    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
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
     */
    private static String expand(String token, Map<String, String> arguments) {
        String expanded = token;
        for (Map.Entry<String, String> argument : arguments.entrySet()) {
            expanded = expanded.replace("%arg_" + argument.getKey() + "%", argument.getValue());
        }
        return expanded.replace(RAW_ARGUMENTS, arguments.getOrDefault("args", ""));
    }

    /** Whether the module's policy allows this action head; every head but the two privileged ones is allowed. */
    private static boolean permitted(String token, PrivilegedActions allowed) {
        String head = head(token);
        if (CONSOLE_HEAD.equals(head)) {
            return allowed.console();
        }
        if (OP_HEAD.equals(head)) {
            return allowed.op();
        }
        return true;
    }

    /** Write one line naming who triggered a privileged action, when the policy asks for the audit trail. */
    private void audit(String token, PlayerRef actor, PrivilegedActions allowed) {
        String head = head(token);
        if (!allowed.audit() || (!CONSOLE_HEAD.equals(head) && !OP_HEAD.equals(head))) {
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

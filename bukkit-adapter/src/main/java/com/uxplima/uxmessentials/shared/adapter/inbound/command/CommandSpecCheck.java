package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import com.uxplima.uxmessentials.shared.application.command.CommandSpec;
import com.uxplima.uxmessentials.shared.application.command.CommandSpecCatalog;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * Compares what the plugin registers with what {@link CommandSpecCatalog} publishes about it.
 *
 * <p>The command reference is generated from the catalogue, and the commands themselves are built in the adapter,
 * so the two can only drift where a command is added, renamed or regated in one place and not the other. A unit test
 * cannot build every command without the services behind it, so the comparison runs on enable over the real
 * registrations, and every difference is a warning that names what to fix. A command an operator defines in a file
 * declares itself as content and is not compared.
 */
@NullMarked
public final class CommandSpecCheck {

    /**
     * A sender that holds nothing. A {@link Gate} names the node that refuses a sender, so asking it about this one
     * reads back the node the command's root is gated by.
     */
    private static final CommandSender NOBODY = (CommandSender) Proxy.newProxyInstance(
            CommandSender.class.getClassLoader(),
            new Class<?>[] {CommandSender.class},
            (proxy, method, args) -> method.getReturnType() == boolean.class ? Boolean.FALSE : null);

    private CommandSpecCheck() {}

    /** Every difference between {@code registrations} and the catalogue, one readable line each. */
    public static List<String> problems(List<CommandRegistration> registrations) {
        Objects.requireNonNull(registrations, "registrations");
        List<String> problems = new ArrayList<>();
        for (CommandRegistration registration : registrations) {
            if (registration.declared()) {
                problems.addAll(problems(registration));
            }
        }
        return List.copyOf(problems);
    }

    private static List<String> problems(CommandRegistration registration) {
        String id = registration.commandId();
        Optional<CommandSpec> found = CommandSpecCatalog.find(id);
        if (found.isEmpty()) {
            return List.of("/" + registration.defaultName() + " (id " + id + ") is registered but CommandSpecCatalog"
                    + " does not list it, so the command reference leaves it out");
        }
        CommandSpec spec = found.get();
        List<String> problems = new ArrayList<>();
        if (!spec.name().equals(registration.defaultName())) {
            problems.add("/" + registration.defaultName() + " (id " + id + ") is listed in CommandSpecCatalog as /"
                    + spec.name());
        }
        List<String> unlisted = registration.defaultAliases().stream()
                .filter(alias -> !spec.aliases().contains(alias))
                .toList();
        if (!unlisted.isEmpty()) {
            problems.add("/" + spec.name() + " answers to " + String.join(", ", unlisted)
                    + ", which CommandSpecCatalog does not list");
        }
        gatedBy(registration).ifPresent(node -> {
            if (!node.equals(spec.permission())) {
                problems.add("/" + spec.name() + " is gated by " + node + " but CommandSpecCatalog says "
                        + spec.permission());
            }
        });
        return problems;
    }

    /**
     * The node the root of {@code registration} is gated by, or empty when the root carries no {@link Gate}, as for a
     * command that checks its node inside the executor so a refused sender reads a message in their language.
     */
    static Optional<String> gatedBy(CommandRegistration registration) {
        if (registration.build().getRequirement() instanceof Gate gate) {
            return gate.refusingNode(NOBODY);
        }
        return Optional.empty();
    }
}

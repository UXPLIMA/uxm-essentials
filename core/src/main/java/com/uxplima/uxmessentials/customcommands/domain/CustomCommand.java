package com.uxplima.uxmessentials.customcommands.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One operator-defined command, whole: what it is called, who may run it, what it accepts, what it costs, and what
 * it does. Read from a single file under {@code commands/custom/} and never mutated afterwards, so a reload swaps
 * a fresh instance in rather than editing a running one.
 *
 * @param id the file identity, and the key an override is keyed against
 * @param literal the word the command registers under, plus its aliases
 * @param permission the node that gates both visibility and execution; empty means everybody may run it
 * @param denyMessage the operator's own refusal line, shown instead of the shared one when the permission fails
 * @param consoleAllowed whether a non-player sender may run the chain
 * @param description the sentence the command listing shows
 * @param usage the usage hint an operator wrote, shown with the description
 * @param cooldown how long a player waits between runs; zero means no gate
 * @param warmup how long a player stands still before the chain runs; zero means no wait
 * @param cost what running it costs, in the configured currency; zero means free
 * @param arguments the declared positional arguments, in order
 * @param requirements the requirement tokens every run must satisfy, an AND gate
 * @param requirementDeny the chain run when the requirements fail, which is how an else branch is written
 * @param actions the chain run when every gate opens
 */
public record CustomCommand(
        CustomCommandId id,
        CommandLiteral literal,
        Optional<String> permission,
        Optional<String> denyMessage,
        boolean consoleAllowed,
        String description,
        Optional<String> usage,
        Duration cooldown,
        Duration warmup,
        double cost,
        List<CommandArgument> arguments,
        List<String> requirements,
        ActionChain requirementDeny,
        ActionChain actions) {

    public CustomCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(literal, "literal");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(denyMessage, "denyMessage");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(warmup, "warmup");
        Objects.requireNonNull(requirementDeny, "requirementDeny");
        Objects.requireNonNull(actions, "actions");
        if (cooldown.isNegative() || warmup.isNegative()) {
            throw new IllegalArgumentException("cooldown and warmup must not be negative");
        }
        if (cost < 0) {
            throw new IllegalArgumentException("cost must not be negative: " + cost);
        }
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        List<String> problems = ArgumentList.validate(arguments);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("invalid arguments for '" + id + "': " + String.join("; ", problems));
        }
    }

    /** A copy carrying {@code replacement} in place of the current literal, for a collision-trimmed alias list. */
    public CustomCommand withLiteral(CommandLiteral replacement) {
        return new CustomCommand(
                id,
                replacement,
                permission,
                denyMessage,
                consoleAllowed,
                description,
                usage,
                cooldown,
                warmup,
                cost,
                arguments,
                requirements,
                requirementDeny,
                actions);
    }

    /** Whether running this command costs money, so a free command never reaches the economy at all. */
    public boolean charged() {
        return cost > 0;
    }
}

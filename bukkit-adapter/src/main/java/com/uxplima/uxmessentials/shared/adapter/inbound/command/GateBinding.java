package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmlib.command.CommandGates;
import com.uxplima.uxmlib.command.Gate;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * Seals every published command's {@link Gate}s, so a sender a gate refuses reads the catalog line in their language
 * instead of Brigadier's "Incorrect argument for command".
 *
 * <p>Runs right before the {@link LocaleBinding}: the refusal is then answered inside the locale scope the binding
 * opens, like every other line a command sends. A refusal for a permission sends {@code command.no-permission}; one
 * for anything else, a feature the server turned off, sends {@code command.unavailable}.
 */
@NullMarked
public final class GateBinding {

    private final CommandFeedback feedback;

    public GateBinding(Messages messages) {
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /** {@code registration} with its tree sealed. */
    public CommandRegistration wrap(CommandRegistration registration) {
        return new SealedRegistration(Objects.requireNonNull(registration, "registration"), this);
    }

    private void refuse(CommandSourceStack source, Gate gate) {
        Optional<String> node = gate.refusingNode(source.getSender());
        if (node.isPresent()) {
            feedback.send(
                    Sender.audience(source), SharedMessageKey.COMMAND_NO_PERMISSION, Map.of("permission", node.get()));
        } else {
            feedback.send(Sender.audience(source), SharedMessageKey.COMMAND_UNAVAILABLE);
        }
    }

    private record SealedRegistration(CommandRegistration delegate, GateBinding binding)
            implements CommandRegistration {

        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return CommandGates.seal(delegate.build(), binding::refuse);
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public List<String> aliases() {
            return delegate.aliases();
        }

        @Override
        public String commandId() {
            return delegate.commandId();
        }

        @Override
        public String defaultName() {
            return delegate.defaultName();
        }

        @Override
        public List<String> defaultAliases() {
            return delegate.defaultAliases();
        }

        @Override
        public Optional<Command<CommandSourceStack>> guiRoot() {
            return delegate.guiRoot();
        }
    }
}

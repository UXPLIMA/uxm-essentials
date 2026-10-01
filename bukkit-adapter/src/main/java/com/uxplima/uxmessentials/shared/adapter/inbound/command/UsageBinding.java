package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * Gives every literal node that requires arguments but carries no executor of its own (the {@code /gamemode}/
 * {@code /pay}/{@code /msg} root, and every intermediate subcommand such as {@code /warp create}) a usage
 * executor that replies with that node's usage line, instead of letting the incomplete input fall through to
 * Brigadier's red "Unknown or incomplete command". Injection recurses the whole tree, so typing a command up to
 * any incomplete point prints the usage for that exact node ({@code /warp create} answers
 * {@code /warp create <name>}).
 *
 * <p>This sits between the {@link CatalogBinding} and the {@link LocaleBinding} at the registration
 * chokepoint: after any rename it sees the effective literal, and before the locale wrap so the usage
 * executor runs inside the bound locale scope and resolves {@link SharedMessageKey#COMMAND_USAGE} in the
 * sender's language. A node that already has an executor is left untouched (same executor instance), so a
 * command that lists or toggles on bare input keeps doing so; a leaf needs no prompt. Each node's requirement
 * predicate is carried across verbatim, so a player without permission still gets the vanilla no-permission
 * response and only a permitted player running an incomplete command sees the usage line.
 */
@NullMarked
public final class UsageBinding {

    private final Messages messages;

    public UsageBinding(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Wrap {@code registration} so a bare arg-only command answers with its usage line. */
    public CommandRegistration wrap(CommandRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        return new BoundRegistration(registration, this);
    }

    private LiteralCommandNode<CommandSourceStack> inject(
            LiteralCommandNode<CommandSourceStack> node, Description description) {
        return literalBuilder(node, node.getLiteral(), description).build();
    }

    /**
     * Rebuild {@code node} carrying its requirement and executor, giving it a usage executor when it has children
     * but no executor of its own, and recursing into every literal descendant so each incomplete point in the tree
     * prints its own usage line ({@code /warp create} answers {@code /warp create <name>}, not the vanilla error).
     * {@code path} is the space-joined literals from the root down to {@code node}, so a nested prompt names the full
     * command. A node that already carries an executor keeps it untouched; only an intermediate literal that has no
     * executor gains the usage prompt.
     */
    private LiteralArgumentBuilder<CommandSourceStack> literalBuilder(
            LiteralCommandNode<CommandSourceStack> node, String path, Description description) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(node.getLiteral());
        if (node.getRequirement() != null) {
            builder.requires(node.getRequirement());
        }
        Command<CommandSourceStack> executor = node.getCommand();
        if (executor instanceof GuiRootBinding.GuiRoot screen
                && screen.fallback() == null
                && !node.getChildren().isEmpty()) {
            // A root that only opens a screen: the console reads the usage line rather than nothing.
            builder.executes(screen.withFallback(usageExecutor(path, node, description)));
        } else if (executor != null) {
            builder.executes(executor);
        } else if (!node.getChildren().isEmpty()) {
            builder.executes(usageExecutor(path, node, description));
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            builder.then(rebindChild(child, path, description));
        }
        return builder;
    }

    /** Recurse usage injection into a literal child; an argument child keeps its subtree verbatim (no usage on an arg). */
    private ArgumentBuilder<CommandSourceStack, ?> rebindChild(
            CommandNode<CommandSourceStack> child, String parentPath, Description description) {
        if (child instanceof LiteralCommandNode<CommandSourceStack> literal) {
            return literalBuilder(literal, parentPath + " " + literal.getLiteral(), description);
        }
        return BrigadierNodes.rebindChild(child);
    }

    /** A usage line for whoever typed the bare command, listing what they can run. */
    private Command<CommandSourceStack> usageExecutor(
            String command, CommandNode<CommandSourceStack> node, Description description) {
        return ctx -> {
            reply(Sender.audience(ctx.getSource()), command, BrigadierUsage.of(node, ctx.getSource()), description);
            return Command.SINGLE_SUCCESS;
        };
    }

    /** Send the usage line. */
    private void reply(CommandSender sender, String command, String usage, Description description) {
        CommandUsage.send(messages, sender, command, usage, description.english());
    }

    /** A {@link CommandRegistration} whose built tree gains a usage root executor when it lacks one. */
    private record BoundRegistration(CommandRegistration delegate, UsageBinding binding)
            implements CommandRegistration {

        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return binding.inject(delegate.build(), new Description(delegate.commandId(), delegate.description()));
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

    /** Which command a usage line describes, and the code's English for it when the catalogue has no line. */
    private record Description(String commandId, String english) {}
}

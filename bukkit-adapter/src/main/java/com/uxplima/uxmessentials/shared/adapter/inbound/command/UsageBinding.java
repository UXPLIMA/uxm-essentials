package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
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
            LiteralCommandNode<CommandSourceStack> node, String description) {
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
            LiteralCommandNode<CommandSourceStack> node, String path, String description) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(node.getLiteral());
        if (node.getRequirement() != null) {
            builder.requires(node.getRequirement());
        }
        Command<CommandSourceStack> executor = node.getCommand();
        if (executor != null) {
            builder.executes(executor);
        } else if (!node.getChildren().isEmpty()) {
            builder.executes(usageExecutor(path, BrigadierUsage.of(node), description));
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            builder.then(rebindChild(child, path, description));
        }
        return builder;
    }

    /** Recurse usage injection into a literal child; an argument child keeps its subtree verbatim (no usage on an arg). */
    private ArgumentBuilder<CommandSourceStack, ?> rebindChild(
            CommandNode<CommandSourceStack> child, String parentPath, String description) {
        if (child instanceof LiteralCommandNode<CommandSourceStack> literal) {
            return literalBuilder(literal, parentPath + " " + literal.getLiteral(), description);
        }
        return BrigadierNodes.rebindChild(child);
    }

    private Command<CommandSourceStack> usageExecutor(String command, String usage, String description) {
        return ctx -> {
            reply(ctx.getSource().getSender(), command, usage, description);
            return Command.SINGLE_SUCCESS;
        };
    }

    private void reply(CommandSender sender, String command, String usage, String description) {
        Map<String, String> placeholders = Map.of("command", command, "usage", usage, "description", description);
        String rendered = messages.resolve(refOf(sender), SharedMessageKey.COMMAND_USAGE, placeholders);
        sender.sendMessage(MiniMessage.miniMessage().deserialize(rendered, StyleTags.resolver()));
    }

    private static PlayerRef refOf(CommandSender sender) {
        return sender instanceof Player player ? BukkitRefs.toRef(player) : PlayerRef.system(sender.getName());
    }

    /** A {@link CommandRegistration} whose built tree gains a usage root executor when it lacks one. */
    private record BoundRegistration(CommandRegistration delegate, UsageBinding binding)
            implements CommandRegistration {

        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return binding.inject(delegate.build(), delegate.description());
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

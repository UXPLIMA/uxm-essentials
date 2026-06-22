package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.mojang.brigadier.Command;
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
 * Gives a command that requires arguments but carries no root executor (the {@code /gamemode}/{@code /pay}/
 * {@code /msg} shape) a default root executor that replies with the command's usage, instead of letting bare
 * input fall through to Brigadier's red "Unknown or incomplete command".
 *
 * <p>This sits between the {@link CatalogBinding} and the {@link LocaleBinding} at the registration
 * chokepoint: after any rename it sees the effective literal, and before the locale wrap so the usage
 * executor runs inside the bound locale scope and resolves {@link SharedMessageKey#COMMAND_USAGE} in the
 * sender's language. A root that already has an executor is returned untouched (same executor instance), so
 * a command that lists or toggles on bare input keeps doing so. The root requirement predicate is carried
 * across verbatim, so a player without permission still gets the vanilla no-permission response and only a
 * permitted player running bare sees the usage line.
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
        if (node.getCommand() != null || node.getChildren().isEmpty()) {
            return node; // already executable, or a leaf that needs no usage prompt
        }
        String literal = node.getLiteral();
        String usage = BrigadierUsage.of(node);
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(literal);
        if (node.getRequirement() != null) {
            builder.requires(node.getRequirement());
        }
        builder.executes(usageExecutor(literal, usage, description));
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            builder.then(BrigadierNodes.rebindChild(child));
        }
        return builder.build();
    }

    private Command<CommandSourceStack> usageExecutor(String literal, String usage, String description) {
        return ctx -> {
            reply(ctx.getSource().getSender(), literal, usage, description);
            return Command.SINGLE_SUCCESS;
        };
    }

    private void reply(CommandSender sender, String literal, String usage, String description) {
        Map<String, String> placeholders = Map.of("command", literal, "usage", usage, "description", description);
        String rendered = messages.resolve(refOf(sender), SharedMessageKey.COMMAND_USAGE, placeholders);
        sender.sendMessage(MiniMessage.miniMessage().deserialize(rendered, StyleTags.resolver()));
    }

    private static PlayerRef refOf(CommandSender sender) {
        return sender instanceof Player player
                ? BukkitRefs.toRef(player)
                : new PlayerRef(new UUID(0L, 0L), sender.getName());
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

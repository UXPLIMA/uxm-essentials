package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Installs a command's GUI opener as its bare-input root executor when the catalog's {@code gui} flag is on.
 *
 * <p>This sits between the {@link CatalogBinding} and the {@link UsageBinding} at the registration
 * chokepoint. A command that opens a screen on bare input ({@code /kit}, {@code /warp}, {@code /uxmess})
 * exposes its opener through {@link CommandRegistration#guiRoot()}; when the resolved {@link EffectiveCommand}
 * for that id has {@code gui} on, the root literal is rebuilt with the opener as its root executor
 * replacing any executor the command shipped with. When {@code gui} is off, the node is returned untouched,
 * so a root that carries no executor falls through to the {@link UsageBinding} and answers with its usage
 * text instead. A command with no entry in the catalog map defaults to gui-on, matching the global default
 * an untouched install ships with.
 *
 * <p>Running before the {@link UsageBinding} is deliberate: the usage injection only fires on a root that
 * still lacks an executor, so a gui-on command has already gained its opener and is left alone, while a
 * gui-off command keeps its bare root open for the usage fallback.
 *
 * <p>The opener runs for a player only. Anyone else, the console first, runs the root the command shipped with:
 * the console cannot see a screen, and {@code /banlist}, {@code /eco} and {@code /uxmess} used to answer it with
 * nothing at all or refuse it as player-only. A root that shipped with nothing to run gets its usage line from the
 * {@link UsageBinding}, through {@link GuiRoot#withFallback}.
 */
@NullMarked
public final class GuiRootBinding {

    private final Map<String, EffectiveCommand> byId;

    public GuiRootBinding(Map<String, EffectiveCommand> byId) {
        this.byId = Map.copyOf(Objects.requireNonNull(byId, "byId"));
    }

    /** Wrap {@code registration} so its bare root opens the GUI when the catalog's {@code gui} flag is on. */
    public CommandRegistration wrap(CommandRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        return new BoundRegistration(registration, this);
    }

    private boolean guiOn(String commandId) {
        EffectiveCommand effective = byId.get(commandId);
        return effective == null || effective.gui();
    }

    /**
     * The bare root of a command that opens a screen: the screen for the player the command acts for, and the
     * command's own root for any other sender.
     */
    public record GuiRoot(
            Command<CommandSourceStack> opener, @Nullable Command<CommandSourceStack> fallback)
            implements Command<CommandSourceStack> {

        public GuiRoot {
            Objects.requireNonNull(opener, "opener");
        }

        @Override
        public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
            if (fallback == null || Sender.actingPlayer(context.getSource()).isPresent()) {
                return opener.run(context);
            }
            return fallback.run(context);
        }

        /** The same root, with {@code fallback} for a sender who is not a player. */
        public GuiRoot withFallback(Command<CommandSourceStack> fallback) {
            return new GuiRoot(opener, Objects.requireNonNull(fallback, "fallback"));
        }
    }

    /** A {@link CommandRegistration} whose bare root gains the GUI opener when its catalog flag is on. */
    private record BoundRegistration(CommandRegistration delegate, GuiRootBinding binding)
            implements CommandRegistration {

        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            LiteralCommandNode<CommandSourceStack> node = delegate.build();
            Optional<Command<CommandSourceStack>> opener = delegate.guiRoot();
            if (opener.isEmpty() || !binding.guiOn(delegate.commandId())) {
                return node;
            }
            return BrigadierNodes.rebindRoot(node, node.getLiteral(), new GuiRoot(opener.get(), node.getCommand()));
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

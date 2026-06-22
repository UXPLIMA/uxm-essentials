package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import org.jspecify.annotations.NullMarked;

/**
 * Applies the resolved command catalog to a list of code-side registrations at the inbound boundary: it
 * renames the root literal, swaps the alias list, and drops disabled commands before they ever reach
 * {@code LifecycleEvents.COMMANDS} (docs/01-architecture, command catalog).
 *
 * <p>A registration is keyed by its stable {@link CommandRegistration#commandId()}; an id with no catalog
 * entry passes through untouched (an untouched install is all code defaults), so the only commands this
 * rewrites are the ones an operator actually overrode. The rewrite is deliberately naming-only: the tree
 * is rebuilt with a new root literal but the children and every executor are carried across verbatim, so
 * renaming {@code /home} to {@code /ev} cannot change what the command does. The {@code commandId} stays
 * pinned to the code-side id across a rename, which is what lets the later registration chokepoint re-key
 * the renamed command back to its permission node.
 */
@NullMarked
public final class CatalogBinding {

    private final Map<String, EffectiveCommand> byId;

    public CatalogBinding(Map<String, EffectiveCommand> byId) {
        this.byId = Map.copyOf(Objects.requireNonNull(byId, "byId"));
    }

    /** Rename/realias the overridden registrations and drop the disabled ones, leaving the rest as-is. */
    public List<CommandRegistration> apply(List<CommandRegistration> registrations) {
        Objects.requireNonNull(registrations, "registrations");
        List<CommandRegistration> result = new ArrayList<>(registrations.size());
        for (CommandRegistration registration : registrations) {
            EffectiveCommand effective = byId.get(registration.commandId());
            if (effective == null) {
                result.add(registration);
            } else if (effective.enabled()) {
                result.add(new BoundRegistration(registration, effective));
            }
        }
        return List.copyOf(result);
    }

    /** A {@link CommandRegistration} whose root literal and aliases come from the catalog entry. */
    private record BoundRegistration(CommandRegistration delegate, EffectiveCommand effective)
            implements CommandRegistration {

        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            // Naming only: carry the existing root executor across, so a rename never changes what the
            // command does. The GuiRootBinding (which runs after this) decides the bare-input behaviour.
            LiteralCommandNode<CommandSourceStack> node = delegate.build();
            return BrigadierNodes.rebindRoot(node, effective.name(), node.getCommand());
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public List<String> aliases() {
            return effective.aliases();
        }

        @Override
        public String commandId() {
            return delegate.commandId();
        }

        @Override
        public Optional<Command<CommandSourceStack>> guiRoot() {
            return delegate.guiRoot();
        }
    }
}

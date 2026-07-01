package com.uxplima.uxmessentials.custommenus.adapter.inbound.command;

import java.util.List;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The operator-declared open command for one custom menu: a menu's {@code command {}} block ({@code /shop},
 * {@code /store}) opens that menu's spec through the public {@link Menus} facade, exactly as {@code /menu open
 * <id>} does, but with the menu's own name, aliases, permission gate and deny message.
 *
 * <p>Unlike {@code /menu}, the permission is checked inside the executor rather than as a Brigadier {@code requires}
 * gate: a {@code requires} gate would hide the command from a sender who lacks the node, giving an "unknown command"
 * rather than the operator's configured deny message. So the command is always visible and the executor decides —
 * a console sender is turned away unless the block allows it, a missing permission draws the deny message (or the
 * shared no-permission line when none is configured), and only then does a real player open the menu.
 */
@NullMarked
public final class MenuOpenCommand implements CommandRegistration {

    private final Menus menus;
    private final String menuId;
    private final OpenCommandSpec spec;
    private final CommandFeedback feedback;

    public MenuOpenCommand(Menus menus, String menuId, OpenCommandSpec spec, Messages messages) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.menuId = Objects.requireNonNull(menuId, "menuId");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(spec.name()).executes(this::open).build();
    }

    @Override
    public List<String> aliases() {
        return spec.aliases();
    }

    @Override
    public String description() {
        return "Opens the " + menuId + " custom menu.";
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        // A non-player sender is turned away first unless the block explicitly allows the console.
        if (!(sender instanceof Player) && !spec.consoleAllowed()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_CONSOLE_DENIED);
            return 0;
        }
        if (spec.permission().isPresent()
                && !sender.hasPermission(spec.permission().get())) {
            denyPermission(sender);
            return 0;
        }
        // Opening a menu needs a player window: a console that cleared the gate above has nobody to open for.
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        // pre-open actions: deferred — needs an engine open-actions seam (openActions-on-open); see ledger.
        menus.open(BukkitRefs.toRef(player), menuId, null);
        return Command.SINGLE_SUCCESS;
    }

    /** Reject a failed permission check with the operator's configured deny line, or the shared default when none. */
    private void denyPermission(CommandSender sender) {
        spec.denyMessage()
                .ifPresentOrElse(
                        line -> sender.sendMessage(StyledText.render(line)),
                        () -> feedback.send(sender, SharedMessageKey.COMMAND_NO_PERMISSION));
    }
}

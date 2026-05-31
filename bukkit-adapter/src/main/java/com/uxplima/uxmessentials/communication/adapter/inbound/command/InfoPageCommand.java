package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.communication.application.CommunicationNotifier;
import com.uxplima.uxmessentials.communication.application.InfoRegistry;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * One auto-registered info-page command — {@code /rules}, {@code /motd}, {@code /info}, or any custom page the
 * operator declared. Built dynamically at module start, one per {@link InfoPage} in the {@link InfoRegistry}, each
 * guarded by its per-page permission node {@code uxmessentials.communication.info.<name>}. The page body is
 * operator-authored MiniMessage content rendered through the {@link BukkitInfoSender}, never a plugin
 * {@code MessageKey}.
 *
 * <p>The page is resolved from the live registry on each run rather than captured, so a
 * {@code /uxmess reload communication} that swaps a fresh registry in serves the new body without re-registering
 * the command. A registry that no longer carries this command's page (an operator removed it before reload took
 * the literal down) replies with the plugin's own {@link CommunicationMessageKey#INFO_PAGE_MISSING} string.
 */
@NullMarked
public final class InfoPageCommand extends CommunicationCommandSupport implements CommandRegistration {

    private static final String PERMISSION_PREFIX = "uxmessentials.communication.info.";

    private final String command;
    private final InfoRegistry registry;
    private final BukkitInfoSender infoSender;
    private final CommunicationNotifier notifier;

    public InfoPageCommand(
            String command,
            InfoRegistry registry,
            BukkitInfoSender infoSender,
            CommunicationNotifier notifier,
            Messages messages) {
        super(messages);
        this.command = Objects.requireNonNull(command, "command");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.infoSender = Objects.requireNonNull(infoSender, "infoSender");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        String permission = PERMISSION_PREFIX + command;
        return Commands.literal(command)
                .requires(src -> src.getSender().hasPermission(permission))
                .executes(this::show)
                .build();
    }

    @Override
    public String description() {
        return "Show the " + command + " info page.";
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<InfoPage> page = registry.find(command);
        if (page.isEmpty()) {
            notifier.send(ref(sender), CommunicationMessageKey.INFO_PAGE_MISSING, Map.of("page", command));
            return Command.SINGLE_SUCCESS;
        }
        infoSender.send(ref(sender), page.get());
        return Command.SINGLE_SUCCESS;
    }
}

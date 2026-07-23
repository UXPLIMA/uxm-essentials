package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.commandcontrol.domain.CommandRateLimiter;
import com.uxplima.uxmessentials.commandcontrol.domain.CommandWindow;
import com.uxplima.uxmessentials.commandcontrol.domain.HidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.NamespaceBypassRule;
import com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.CommandExecuteEvent.CommandResult;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;

/**
 * The Velocity inbound listener for proxy command-control. It reuses the pure {@code :core} domain for
 * every decision and translates the three proxy events:
 *
 * <ul>
 *   <li>{@link PlayerAvailableCommandsEvent} - prunes the client command tree through the
 *       {@link ProxyCommandTreeFilter}, so a non-permitted player never sees hidden / denied proxy
 *       commands in tab-completion or the client command list (a {@code .bypass} holder keeps everything).
 *   <li>{@link CommandExecuteEvent} - runs the command-spam guard first (KICK / BLOCK / WARN), then the
 *       whitelist / blacklist gate and the hidden-command deny-execution, denying the command result and
 *       delivering the configured message on a block.
 *   <li>{@link DisconnectEvent} - drops the player's spam window so the per-uuid map stays bounded.
 * </ul>
 *
 * <p>The spam window map is a {@link ConcurrentHashMap} keyed by uuid and mutated only through
 * {@code compute}, so the count is a single atomic step with no lock and no shared mutable buffer; the
 * pure {@link CommandRateLimiter} owns the window arithmetic. Only players are gated - a console or
 * proxy-plugin command source is left untouched.
 */
public final class CommandControlListener {

    private final RuleSet rules;
    private final HidePolicy hidePolicy;
    private final ProxyCommandTreeFilter treeFilter;
    private final CommandRateLimiter rateLimiter;
    private final ProxyGroupSource groups;
    private final ProxyCommandMessages messages;
    private final boolean blockNamespaceBypass;
    private final boolean denyListCommands;
    private final boolean useUnknownCommandMessage;
    private final LongSupplier clock;
    private final Map<UUID, CommandWindow> windows = new ConcurrentHashMap<>();

    public CommandControlListener(
            RuleSet rules,
            HidePolicy hidePolicy,
            ProxyCommandTreeFilter treeFilter,
            CommandRateLimiter rateLimiter,
            ProxyGroupSource groups,
            ProxyCommandMessages messages,
            boolean blockNamespaceBypass,
            boolean denyListCommands,
            boolean useUnknownCommandMessage) {
        this(
                rules,
                hidePolicy,
                treeFilter,
                rateLimiter,
                groups,
                messages,
                blockNamespaceBypass,
                denyListCommands,
                useUnknownCommandMessage,
                System::currentTimeMillis);
    }

    /** Test seam: the same wiring with the millisecond clock supplied so the sliding window is driven virtually. */
    CommandControlListener(
            RuleSet rules,
            HidePolicy hidePolicy,
            ProxyCommandTreeFilter treeFilter,
            CommandRateLimiter rateLimiter,
            ProxyGroupSource groups,
            ProxyCommandMessages messages,
            boolean blockNamespaceBypass,
            boolean denyListCommands,
            boolean useUnknownCommandMessage,
            LongSupplier clock) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.hidePolicy = Objects.requireNonNull(hidePolicy, "hidePolicy");
        this.treeFilter = Objects.requireNonNull(treeFilter, "treeFilter");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.blockNamespaceBypass = blockNamespaceBypass;
        this.denyListCommands = denyListCommands;
        this.useUnknownCommandMessage = useUnknownCommandMessage;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Subscribe
    public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(ProxyCommandControl.BYPASS)) {
            return;
        }
        treeFilter.filter(event.getRootNode(), facts(player));
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        CommandSource source = event.getCommandSource();
        if (!(source instanceof Player player)) {
            return;
        }
        if (applySpam(event, player)) {
            return;
        }
        gate(event, player);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        windows.remove(event.getPlayer().getUniqueId());
    }

    /** Run the spam guard; return {@code true} when it denied the command (KICK / BLOCK) so the gate is skipped. */
    private boolean applySpam(CommandExecuteEvent event, Player player) {
        if (!rateLimiter.isEnabled() || player.hasPermission(ProxyCommandControl.SPAM_BYPASS)) {
            return false;
        }
        if (!recordAndCheck(player.getUniqueId())) {
            return false;
        }
        return switch (rateLimiter.action()) {
            case KICK -> {
                messages.spamKickComponent().ifPresent(player::disconnect);
                event.setResult(CommandResult.denied());
                yield true;
            }
            case BLOCK -> {
                messages.spamBlockedComponent().ifPresent(player::sendMessage);
                event.setResult(CommandResult.denied());
                yield true;
            }
            case WARN -> {
                messages.spamWarnComponent().ifPresent(player::sendMessage);
                yield false;
            }
        };
    }

    /** Block a disallowed / hidden command's execution, denying the result and delivering the deny line. */
    private void gate(CommandExecuteEvent event, Player player) {
        String root = commandRoot(event.getCommand());
        if (root.isEmpty()) {
            return;
        }
        PlayerFacts facts = facts(player);
        boolean hidden = denyListCommands && hidePolicy.shouldHide(root, facts);
        if (!hidden && !ruleDenied(root, facts)) {
            return;
        }
        event.setResult(CommandResult.denied());
        if (hidden) {
            messages.pluginHiddenComponent().ifPresent(player::sendMessage);
        } else {
            messages.deny(useUnknownCommandMessage).ifPresent(player::sendMessage);
        }
    }

    /** A root is rule-denied when the rule set denies it, or its namespaced form maps to a denied bare command. */
    private boolean ruleDenied(String root, PlayerFacts facts) {
        if (rules.decide(root, facts) == RuleSet.Decision.DENY) {
            return true;
        }
        if (!blockNamespaceBypass) {
            return false;
        }
        return NamespaceBypassRule.bareRoot(root)
                .map(bare -> rules.decide(bare, facts) == RuleSet.Decision.DENY)
                .orElse(false);
    }

    /** Fold this command into the player's window and report whether it tripped the limit: one atomic {@code compute}. */
    private boolean recordAndCheck(UUID player) {
        long now = clock.getAsLong();
        boolean[] tripped = {false};
        windows.compute(player, (uuid, previous) -> {
            CommandRateLimiter.Evaluation evaluation = rateLimiter.evaluate(previous, now);
            tripped[0] = evaluation.tripped();
            return evaluation.window();
        });
        return tripped[0];
    }

    private PlayerFacts facts(Player player) {
        return new ProxyPlayerFacts(player, groups);
    }

    /** The command label of a raw proxy command: leading slash stripped, arguments dropped, lowercased. */
    private static String commandRoot(String rawCommand) {
        String body = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        return label.toLowerCase(Locale.ROOT);
    }
}

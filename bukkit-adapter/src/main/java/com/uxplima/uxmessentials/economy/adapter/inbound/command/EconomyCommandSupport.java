package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.AmountParseError;
import com.uxplima.uxmessentials.economy.domain.AmountParser;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every economy Brigadier command holds: the constructed {@link EconomyServices} and the
 * {@link Messages} catalog (the latter only for the console-side rejections a player-only command may surface
 * — all player-facing feedback flows through the use cases' {@code MessageSink}). It centralises the three
 * boundary concerns the economy commands repeat: resolving the optional {@code [currency]} argument against
 * the closed registry (an unknown id is an error listing the valid ids, never a silent default), parsing an
 * amount to the currency's precision, and running a provider call off the tick thread through the injected
 * {@code Scheduler} so a foreign provider can never wedge the command (§4.3, §6.2).
 */
@NullMarked
abstract class EconomyCommandSupport {

    /** The catalog key for a console invoking a player-only command. */
    static final MessageKey PLAYERS_ONLY = SharedMessageKey.COMMAND_PLAYERS_ONLY;

    final EconomyServices services;
    final CommandFeedback feedback;

    EconomyCommandSupport(EconomyServices services, Messages messages) {
        this.services = Objects.requireNonNull(services, "services");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        sendConsole(sender, PLAYERS_ONLY);
        return null;
    }

    /** Resolve the {@code [currency]} argument against the registry, defaulting to the configured default. */
    final Optional<Currency> currency(CommandContext<CommandSourceStack> ctx) {
        if (!hasArgument(ctx)) {
            return Optional.of(services.currencies().defaultCurrency());
        }
        String raw = ctx.getArgument("currency", String.class);
        return services.currencies().find(CurrencyId.of(raw));
    }

    /** The default currency — the one every command resolves to when {@code [currency]} is omitted. */
    final Currency defaultCurrency() {
        return services.currencies().defaultCurrency();
    }

    /**
     * Parse a raw amount into {@code currency}, accepting magnitude suffixes ({@code 10k}, {@code 1.5m}) and
     * thousands separators, clamped to the currency range. On a parse failure the rejection is sent to
     * {@code viewer} and an empty {@link Optional} returns, so a caller only proceeds with a usable figure.
     */
    final Optional<Money> amount(String raw, Currency currency, PlayerRef viewer) {
        Result<Money, AmountParseError> parsed = AmountParser.parse(raw, currency);
        if (parsed.isErr()) {
            services.notifier().send(viewer, parsed.errorOrThrow().messageKey());
            return Optional.empty();
        }
        return Optional.of(parsed.orElseThrow());
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /** Run {@code task} off the tick thread, so a (possibly foreign) provider call never blocks the command. */
    final void offTick(Runnable task) {
        services.scheduler().async(task);
    }

    /** Send a console-only catalog message to a non-player sender. */
    final void sendConsole(CommandSender sender, MessageKey key) {
        feedback.send(sender, key, Map.of());
    }

    /** Tell {@code viewer} the named currency is not configured, listing the valid ids. */
    final void rejectUnknownCurrency(PlayerRef viewer) {
        String valid = services.currencies().ids().stream()
                .map(CurrencyId::value)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        services.notifier().send(viewer, EconomyMessageKey.CURRENCY_UNKNOWN, Map.of("currencies", valid));
    }

    /** Tell {@code viewer} the named pay target could not be resolved. */
    final void rejectUnknownTarget(PlayerRef viewer, String name) {
        services.notifier().send(viewer, EconomyMessageKey.PAY_TARGET_UNKNOWN, Map.of("player", name));
    }

    private boolean hasArgument(CommandContext<CommandSourceStack> ctx) {
        try {
            ctx.getArgument("currency", String.class);
            return true;
        } catch (IllegalArgumentException absent) {
            return false;
        }
    }
}

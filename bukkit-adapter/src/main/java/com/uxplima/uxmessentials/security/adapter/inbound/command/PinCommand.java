package com.uxplima.uxmessentials.security.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.security.application.PinSetResult;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.security.application.SetPin;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /pin set <pin>}: set the numeric PIN second factor. The PIN is validated against the configured length
 * policy, then hashed and stored — the hashing (a deliberately slow PBKDF2) and the DB write run off the tick thread
 * through the {@link Scheduler}. Gated on {@code uxmessentials.security.pin}, which ships {@code true}. The plaintext
 * PIN is never logged; only its salted one-way hash is stored.
 */
@NullMarked
public final class PinCommand extends SecurityCommandSupport implements CommandRegistration {

    /** The self-service permission every player holds to set their own PIN. */
    public static final String PERMISSION = "uxmessentials.security.pin";

    private final SetPin setPin;
    private final SecurityConfig.TwoFactorSettings settings;

    public PinCommand(
            SetPin setPin,
            SecurityConfig.TwoFactorSettings settings,
            Scheduler scheduler,
            Messages messages,
            MessageSink sink) {
        super(scheduler, messages, sink);
        this.setPin = Objects.requireNonNull(setPin, "setPin");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pin")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("set")
                        .executes(this::usage)
                        .then(Commands.argument("pin", StringArgumentType.word())
                                .executes(this::set)))
                .executes(this::usage)
                .build();
    }

    @Override
    public String description() {
        return "/pin set <pin> to set your numeric PIN second factor.";
    }

    private int usage(CommandContext<CommandSourceStack> ctx) {
        reply(ctx.getSource().getSender(), SecurityMessageKey.SECURITY_PIN_USAGE);
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Player player = requirePlayer(sender);
        if (player == null) {
            return 0;
        }
        if (!settings.enabled() || !settings.pin()) {
            reply(sender, SecurityMessageKey.SECURITY_PIN_FEATURE_DISABLED);
            return 0;
        }
        String pin = ctx.getArgument("pin", String.class);
        PlayerRef who = ref(player);
        scheduler.async(() -> deliverResult(who, setPin.set(who.uuid(), pin)));
        return Command.SINGLE_SUCCESS;
    }

    private void deliverResult(PlayerRef who, PinSetResult result) {
        switch (result) {
            case SET -> notify(who, SecurityMessageKey.SECURITY_PIN_SET);
            case TOO_SHORT ->
                notify(
                        who,
                        SecurityMessageKey.SECURITY_PIN_TOO_SHORT,
                        Map.of("min", String.valueOf(settings.pinPolicy().minLength())));
            case TOO_LONG ->
                notify(
                        who,
                        SecurityMessageKey.SECURITY_PIN_TOO_LONG,
                        Map.of("max", String.valueOf(settings.pinPolicy().maxLength())));
            case NOT_NUMERIC -> notify(who, SecurityMessageKey.SECURITY_PIN_NOT_NUMERIC);
        }
    }
}

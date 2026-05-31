package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.playerstate.domain.PersonalWeather;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /pweather <clear|rain|reset>} ({@code uxmessentials.pweather.use}): set a per-player client-side
 * weather without changing world weather. Self-only. The argument is parsed to a {@link PersonalWeather}; an
 * unrecognised value is rejected with {@link PlayerstateMessageKey#PWEATHER_INVALID}. The
 * {@code SetPersonalWeather} use case owns applying it and confirming.
 */
@NullMarked
public final class PersonalWeatherCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.pweather.use";

    public PersonalWeatherCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pweather")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("value", StringArgumentType.word()).executes(this::set))
                .build();
    }

    @Override
    public String description() {
        return "Set your personal weather.";
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PersonalWeather> weather = PersonalWeather.parse(ctx.getArgument("value", String.class));
        if (weather.isEmpty()) {
            sender.sendMessage(MiniMessage.miniMessage()
                    .deserialize(messages.resolve(ref(sender), PlayerstateMessageKey.PWEATHER_INVALID, Map.of())));
            return 0;
        }
        services.personalWeather().apply(ref(sender), weather.get());
        return Command.SINGLE_SUCCESS;
    }
}

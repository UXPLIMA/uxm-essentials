package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.playerstate.domain.PersonalTime;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /ptime <value|reset>} ({@code uxmessentials.ptime.use}): set a per-player client-side time without
 * changing world time. Self-only. The argument is parsed to a {@link PersonalTime} (a tick count, a named
 * preset, or {@code reset}); an unparseable value is rejected with {@link PlayerstateMessageKey#PTIME_INVALID}.
 * The {@code SetPersonalTime} use case owns applying it and confirming.
 *
 * <p>The named presets and {@code reset} are offered in tab-completion so the return-to-server-time form is
 * discoverable next to the day/night presets, mirroring how {@code /pweather} surfaces its {@code reset}.
 */
@NullMarked
public final class PersonalTimeCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.ptime.use";

    // The discoverable tokens PersonalTime.parse accepts, with reset offered alongside the named presets so the
    // return-to-server-time form shows in tab-completion. A raw tick count still parses without being suggested.
    private static final List<String> VALUES =
            List.of("day", "noon", "sunset", "night", "midnight", "sunrise", "reset");

    public PersonalTimeCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ptime")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(CommandSuggestions.fromStrings(() -> VALUES))
                        .executes(this::set))
                .build();
    }

    @Override
    public String description() {
        return "Set your personal time.";
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PersonalTime> time = PersonalTime.parse(ctx.getArgument("value", String.class));
        if (time.isEmpty()) {
            feedback.send(sender, PlayerstateMessageKey.PTIME_INVALID, Map.of());
            return 0;
        }
        services.personalTime().apply(ref(sender), time.get());
        return Command.SINGLE_SUCCESS;
    }
}

package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitWeatherApplier;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.itemworld.domain.WeatherSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /weather <clear|rain|thunder> [duration]}: set the world's weather, optionally time-boxed to a
 * duration in seconds. The kind and duration are validated by the domain {@link WeatherSpec} before the world is
 * touched (else {@link ItemworldMessageKey#BAD_WEATHER}); the {@code /sun}, {@code /rain} and {@code /thunder}
 * alias verbs share this group through their own class.
 *
 * <p>World weather is global game state, so the change runs on the global region thread through the kernel
 * {@code Scheduler#onGlobal} via {@link BukkitWeatherApplier}; the result is reported through
 * {@link ItemworldMessageKey#WEATHER_SET}.
 */
@NullMarked
public final class WeatherCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.weather.use";

    public WeatherCommand(ItemworldServices services) {
        super(services, "weather", SubFeatureGroup.TIME_WEATHER, "Set the weather.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("kind", StringArgumentType.word())
                        .executes(ctx -> run(ctx, Optional.empty()))
                        .then(Commands.argument("duration", IntegerArgumentType.integer(0))
                                .executes(
                                        ctx -> run(ctx, Optional.of(IntegerArgumentType.getInteger(ctx, "duration"))))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<Integer> duration) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<WeatherSpec> spec = WeatherSpec.parse(ctx.getArgument("kind", String.class), duration);
        if (spec.isEmpty()) {
            reply(ctx, ItemworldMessageKey.BAD_WEATHER);
            return Command.SINGLE_SUCCESS;
        }
        apply(ctx, player.getWorld(), spec.get());
        return Command.SINGLE_SUCCESS;
    }

    private void apply(CommandContext<CommandSourceStack> ctx, World world, WeatherSpec spec) {
        services.kernel().scheduler().onGlobal(() -> {
            BukkitWeatherApplier.apply(world, spec);
            reply(
                    ctx,
                    ItemworldMessageKey.WEATHER_SET,
                    Map.of(
                            "world",
                            world.getName(),
                            "weather",
                            spec.kind().name().toLowerCase(java.util.Locale.ROOT)));
        });
    }
}

package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.domain.SpeedValue;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /speed} family ({@code uxmessentials.speed.use}): {@code /speed <value> [player]} sets both walk
 * and fly speed, while {@code /walkspeed} and {@code /flyspeed} set each independently. One class parameterised
 * by {@link Target} backs all three literals so the {@code value [player]} argument shape and the
 * {@code .others} gate stay in one place. The value is parsed on the {@code 0..10} operator scale (Brigadier
 * bounds it; {@link SpeedValue} clamps any residual) and the {@code SetSpeed} use case owns the mutation.
 */
@NullMarked
public final class SpeedCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.speed.use";

    /** Which speed(s) a given literal sets. */
    public enum Target {
        BOTH("speed", "Set walk and fly speed."),
        WALK("walkspeed", "Set walk speed."),
        FLY("flyspeed", "Set fly speed.");

        private final String literal;
        private final String description;

        Target(String literal, String description) {
            this.literal = literal;
            this.description = description;
        }
    }

    private final Target target;

    public SpeedCommand(Target target, PlayerStateServices services, Messages messages) {
        super(services, messages);
        this.target = Objects.requireNonNull(target, "target");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(target.literal)
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, SpeedValue.MAX_SCALE))
                        .executes(this::set)
                        .then(PlayerTargets.players("player").executes(this::set)))
                .build();
    }

    @Override
    public String description() {
        return target.description;
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        SpeedValue value = SpeedValue.of(ctx.getArgument("value", Double.class));
        for (PlayerRef target : targets) {
            apply(ref(sender), target, value);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void apply(PlayerRef actor, PlayerRef subject, SpeedValue value) {
        switch (target) {
            case BOTH -> services.setSpeed().setBoth(actor, subject, value);
            case WALK -> services.setSpeed().setWalk(actor, subject, value);
            case FLY -> services.setSpeed().setFly(actor, subject, value);
        }
    }
}

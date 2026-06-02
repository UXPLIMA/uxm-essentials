package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the playerstate context's Brigadier command surface (docs/10-feature-modules.md §15.6) as
 * {@link CommandRegistration}s over the constructed {@link PlayerStateServices}. Collected in one greppable
 * table so the literal/permission pairing matches the permissions reference and the kernel's
 * {@code PlayerstateCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS} handler registers each.
 *
 * <p>The {@code /gmc /gms /gma /gmsp} mode shortcuts are separate fixed-mode commands (each pins a
 * {@link GameModeRef}), and {@code /walkspeed} / {@code /flyspeed} are the split forms of {@code /speed}.
 * {@code /repair}, {@code /repairall}, {@code /hat}, and {@code /more} are itemworld-owned and are not here.
 */
@NullMarked
public final class PlayerStateCommands {

    private PlayerStateCommands() {}

    /** Every playerstate command, in surface order. */
    public static List<CommandRegistration> all(PlayerStateServices services, Messages messages) {
        return List.of(
                new GodCommand(services, messages),
                new FlyCommand(services, messages),
                new HealCommand(services, messages),
                new FeedCommand(services, messages),
                new FoodLevelCommand(services, messages),
                new HealthCommand(services, messages),
                new GamemodeCommand(services, messages),
                new GamemodeAliasCommand("gmc", GameModeRef.CREATIVE, services, messages),
                new GamemodeAliasCommand("gms", GameModeRef.SURVIVAL, services, messages),
                new GamemodeAliasCommand("gma", GameModeRef.ADVENTURE, services, messages),
                new GamemodeAliasCommand("gmsp", GameModeRef.SPECTATOR, services, messages),
                new SpeedCommand(SpeedCommand.Target.BOTH, services, messages),
                new SpeedCommand(SpeedCommand.Target.WALK, services, messages),
                new SpeedCommand(SpeedCommand.Target.FLY, services, messages),
                new ExtinguishCommand(services, messages),
                new ClearInventoryCommand(services, messages),
                new SuicideCommand(services, messages),
                new NearCommand(services, messages),
                new NightVisionCommand(services, messages),
                new PersonalTimeCommand(services, messages),
                new PersonalWeatherCommand(services, messages),
                new ExperienceCommand(services, messages),
                new AirCommand(services, messages),
                new BurnCommand(services, messages),
                new GetPosCommand(services, messages),
                new PingCommand(services, messages),
                new RestCommand(services, messages));
    }
}

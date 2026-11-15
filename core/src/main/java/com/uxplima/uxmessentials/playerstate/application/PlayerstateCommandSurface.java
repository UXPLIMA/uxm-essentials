package com.uxplima.uxmessentials.playerstate.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.permission.PlayerstatePermissions;

/**
 * The playerstate context's command surface as platform-neutral
 * {@link CommandSpec}s: each pairs a literal with its base permission node and a factory that produces the
 * kernel-side {@link BrigadierCommand} description. The playerstate inbound adapter realises the Brigadier
 * node from the started module's context on the next {@code COMMANDS} fire. Collected here so
 * {@code PlayerstateModule} stays small and the command/permission pairing is one greppable table.
 *
 * <p>The {@code .others} target form of each self/other command is gated by the single shared
 * {@code uxmessentials.playerstate.others} node, checked per-invocation in the adapter rather than declared
 * as a separate command literal, so it is intentionally absent from this table. The {@code /gmc /gms /gma
 * /gmsp} mode shortcuts, {@code /nv}, and {@code /extinguish} are aliases of their base commands, registered
 * through the {@link BrigadierCommand#aliases()} list, not as separate specs. {@code /repair}, {@code /repairall},
 * {@code /hat}, and {@code /more} belong to the itemworld context and are intentionally not registered here.
 */
final class PlayerstateCommandSurface {

    private PlayerstateCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("god", PlayerstatePermissions.GOD_USE, cmd("god", "Toggle damage immunity")),
                spec("fly", PlayerstatePermissions.FLY_USE, cmd("fly", "Toggle flight")),
                spec("heal", PlayerstatePermissions.HEAL_USE, cmd("heal", "Restore health")),
                spec("feed", PlayerstatePermissions.FEED_USE, cmd("feed", "Restore hunger")),
                spec("foodlevel", PlayerstatePermissions.FOODLEVEL_USE, cmd("foodlevel", "Set a player's food level")),
                spec("health", PlayerstatePermissions.HEALTH_USE, cmd("health", "Set a player's health")),
                spec("gamemode", PlayerstatePermissions.GAMEMODE_USE, cmd("gamemode", "Set game mode")),
                spec("speed", PlayerstatePermissions.SPEED_USE, cmd("speed", "Set walk and fly speed")),
                spec("flyspeed", PlayerstatePermissions.SPEED_USE, cmd("flyspeed", "Set fly speed")),
                spec("walkspeed", PlayerstatePermissions.SPEED_USE, cmd("walkspeed", "Set walk speed")),
                spec("ext", PlayerstatePermissions.EXTINGUISH_USE, cmd("ext", "Put out a burning player")),
                spec(
                        "clearinventory",
                        PlayerstatePermissions.CLEARINVENTORY_USE,
                        cmd("clearinventory", "Clear a player's inventory")),
                spec(
                        "clearinventoryconfirmtoggle",
                        PlayerstatePermissions.CLEARINVENTORY_CONFIRMTOGGLE,
                        cmd("clearinventoryconfirmtoggle", "Toggle a confirmation before /clearinventory")),
                spec("invsee", PlayerstatePermissions.INVSEE_USE, cmd("invsee", "View a player's inventory")),
                spec("endersee", PlayerstatePermissions.ENDERSEE_USE, cmd("endersee", "View a player's ender chest")),
                spec("suicide", PlayerstatePermissions.SUICIDE_USE, cmd("suicide", "Kill yourself")),
                spec("near", PlayerstatePermissions.NEAR_USE, cmd("near", "List nearby players")),
                spec("nightvision", PlayerstatePermissions.NIGHTVISION_USE, cmd("nightvision", "Toggle night vision")),
                spec("glow", PlayerstatePermissions.GLOW_USE, cmd("glow", "Toggle a glowing outline")),
                spec("ptime", PlayerstatePermissions.PTIME_USE, cmd("ptime", "Set your personal time")),
                spec("pweather", PlayerstatePermissions.PWEATHER_USE, cmd("pweather", "Set your personal weather")),
                spec("exp", PlayerstatePermissions.EXP_USE, cmd("exp", "Get or set experience")),
                spec("air", PlayerstatePermissions.AIR_USE, cmd("air", "Set remaining air")),
                spec("burn", PlayerstatePermissions.BURN_USE, cmd("burn", "Set a player on fire")),
                spec("ice", PlayerstatePermissions.ICE_USE, cmd("ice", "Freeze a player")),
                spec("getpos", PlayerstatePermissions.GETPOS_USE, cmd("getpos", "Show a player's coordinates")),
                spec("ping", PlayerstatePermissions.PING_USE, cmd("ping", "Show a player's ping")),
                spec(
                        "playtime",
                        PlayerstatePermissions.PLAYTIME_USE,
                        cmd("playtime", "Show, correct or import a player's playtime")),
                spec("rest", PlayerstatePermissions.REST_USE, cmd("rest", "Reset time-since-rest so phantoms stop")),
                spec(
                        "depth",
                        PlayerstatePermissions.DEPTH_USE,
                        cmd("depth", "Show how far you are above or below sea level")),
                spec("biome", PlayerstatePermissions.BIOME_USE, cmd("biome", "Show the biome you are standing in")),
                spec(
                        "seed",
                        PlayerstatePermissions.SEED_USE,
                        cmd("seed", "Show the seed of the world you are standing in")),
                spec(
                        "compass",
                        PlayerstatePermissions.COMPASS_USE,
                        cmd("compass", "Show the direction you are facing")),
                spec("world", PlayerstatePermissions.WORLD_USE, cmd("world", "Show the world you are standing in")),
                spec(
                        "dimension",
                        PlayerstatePermissions.DIMENSION_USE,
                        cmd("dimension", "Show the dimension you are standing in")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private static BrigadierCommand cmd(String literal, String description) {
        return new PlayerstateCommand(literal, description);
    }

    /** The kernel-side description of one playerstate command, literal and help text, no Brigadier type. */
    private record PlayerstateCommand(String literal, String description) implements BrigadierCommand {}
}

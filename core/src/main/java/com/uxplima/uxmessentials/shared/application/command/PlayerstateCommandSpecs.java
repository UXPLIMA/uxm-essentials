package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.permission.ContentPermissions;
import com.uxplima.uxmessentials.shared.application.permission.PlayerstatePermissions;

/** A player's own state and the survival mechanics they switch for themselves. Data, not logic: one row per command, read by {@link CommandSpecCatalog}. */
final class PlayerstateCommandSpecs {

    private static final ModuleId PLAYERSTATE = ModuleId.of("playerstate");
    private static final ModuleId SURVIVAL = ModuleId.of("survival");
    private static final ModuleId VILLAGERS = ModuleId.of("villagers");

    private PlayerstateCommandSpecs() {}

    static List<CommandSpec> all() {
        return List.of(
                CommandSpec.of(PLAYERSTATE, "air", PlayerstatePermissions.AIR_USE),
                CommandSpec.of(PLAYERSTATE, "biome", PlayerstatePermissions.BIOME_USE),
                CommandSpec.of(PLAYERSTATE, "burn", PlayerstatePermissions.BURN_USE),
                CommandSpec.of(PLAYERSTATE, "clearinventory", PlayerstatePermissions.CLEARINVENTORY_USE, "ci", "clear"),
                CommandSpec.of(
                        PLAYERSTATE,
                        "clearinventoryconfirmtoggle",
                        PlayerstatePermissions.CLEARINVENTORY_CONFIRMTOGGLE,
                        "citoggle"),
                CommandSpec.of(PLAYERSTATE, "compass", PlayerstatePermissions.COMPASS_USE),
                CommandSpec.of(PLAYERSTATE, "depth", PlayerstatePermissions.DEPTH_USE),
                CommandSpec.of(PLAYERSTATE, "dimension", PlayerstatePermissions.DIMENSION_USE, "dim"),
                CommandSpec.of(PLAYERSTATE, "endersee", PlayerstatePermissions.ENDERSEE_USE),
                CommandSpec.of(PLAYERSTATE, "exp", PlayerstatePermissions.EXP_USE, "xp"),
                CommandSpec.of(PLAYERSTATE, "ext", PlayerstatePermissions.EXTINGUISH_USE, "extinguish"),
                CommandSpec.of(PLAYERSTATE, "feed", PlayerstatePermissions.FEED_USE, "eat"),
                CommandSpec.of(PLAYERSTATE, "fly", PlayerstatePermissions.FLY_USE),
                CommandSpec.of(PLAYERSTATE, "flyspeed", PlayerstatePermissions.SPEED_USE),
                CommandSpec.of(PLAYERSTATE, "foodlevel", PlayerstatePermissions.FOODLEVEL_USE),
                CommandSpec.of(PLAYERSTATE, "gamemode", PlayerstatePermissions.GAMEMODE_USE, "gm"),
                CommandSpec.of(PLAYERSTATE, "getpos", PlayerstatePermissions.GETPOS_USE, "coords", "whereami"),
                CommandSpec.of(PLAYERSTATE, "glow", PlayerstatePermissions.GLOW_USE),
                CommandSpec.of(PLAYERSTATE, "gma", PlayerstatePermissions.GAMEMODE_USE),
                CommandSpec.of(PLAYERSTATE, "gmc", PlayerstatePermissions.GAMEMODE_USE),
                CommandSpec.of(PLAYERSTATE, "gms", PlayerstatePermissions.GAMEMODE_USE),
                CommandSpec.of(PLAYERSTATE, "gmsp", PlayerstatePermissions.GAMEMODE_USE),
                CommandSpec.of(PLAYERSTATE, "god", PlayerstatePermissions.GOD_USE, "godmode"),
                CommandSpec.of(PLAYERSTATE, "heal", PlayerstatePermissions.HEAL_USE),
                CommandSpec.of(PLAYERSTATE, "health", PlayerstatePermissions.HEALTH_USE),
                CommandSpec.of(PLAYERSTATE, "ice", PlayerstatePermissions.ICE_USE),
                CommandSpec.of(PLAYERSTATE, "invsee", PlayerstatePermissions.INVSEE_USE),
                CommandSpec.of(PLAYERSTATE, "near", PlayerstatePermissions.NEAR_USE, "nearby"),
                CommandSpec.of(PLAYERSTATE, "nightvision", PlayerstatePermissions.NIGHTVISION_USE, "nv"),
                CommandSpec.of(PLAYERSTATE, "ping", PlayerstatePermissions.PING_USE),
                CommandSpec.of(PLAYERSTATE, "playtime", PlayerstatePermissions.PLAYTIME_USE),
                CommandSpec.of(PLAYERSTATE, "ptime", PlayerstatePermissions.PTIME_USE),
                CommandSpec.of(PLAYERSTATE, "pweather", PlayerstatePermissions.PWEATHER_USE),
                CommandSpec.of(PLAYERSTATE, "rest", PlayerstatePermissions.REST_USE),
                CommandSpec.of(PLAYERSTATE, "seed", PlayerstatePermissions.SEED_USE),
                CommandSpec.of(PLAYERSTATE, "speed", PlayerstatePermissions.SPEED_USE),
                CommandSpec.of(PLAYERSTATE, "suicide", PlayerstatePermissions.SUICIDE_USE),
                CommandSpec.of(PLAYERSTATE, "walkspeed", PlayerstatePermissions.SPEED_USE),
                CommandSpec.of(PLAYERSTATE, "world", PlayerstatePermissions.WORLD_USE),
                CommandSpec.of(SURVIVAL, "autopickup", ContentPermissions.SURVIVAL_AUTOPICKUP_TOGGLE),
                CommandSpec.of(SURVIVAL, "autosell", ContentPermissions.SURVIVAL_AUTOSELL_TOGGLE),
                CommandSpec.of(SURVIVAL, "autosmelt", ContentPermissions.SURVIVAL_AUTOSMELT_TOGGLE),
                CommandSpec.of(SURVIVAL, "autotool", ContentPermissions.SURVIVAL_AUTOTOOL_TOGGLE),
                CommandSpec.of(SURVIVAL, "farmprotect", ContentPermissions.SURVIVAL_FARMPROTECT_TOGGLE),
                CommandSpec.of(SURVIVAL, "survival", ContentPermissions.SURVIVAL_GUI),
                CommandSpec.of(SURVIVAL, "treefeller", ContentPermissions.SURVIVAL_TREEFELLER_TOGGLE),
                CommandSpec.of(SURVIVAL, "veinminer", ContentPermissions.SURVIVAL_VEINMINER_TOGGLE),
                CommandSpec.of(VILLAGERS, "villager", ContentPermissions.VILLAGERS_USE));
    }
}

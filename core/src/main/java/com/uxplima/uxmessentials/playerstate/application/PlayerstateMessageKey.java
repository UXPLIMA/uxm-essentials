package com.uxplima.uxmessentials.playerstate.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The playerstate context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code GOD_ON} ↔ {@code playerstate.god.on}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the context —
 * every message resolves through one of these.
 *
 * <p>Toggle commands carry an {@code .on}/{@code .off} pair plus an {@code .other} variant for the
 * {@code .others} target so a staff toggle confirms whose state changed; the apply-once and verb commands
 * carry a self confirmation and an {@code .other} variant where a target form exists. Per the i18n contract,
 * a disabled module still ships its keys so the catalog stays whole and the locale-parity guard sees the full
 * {@code en} key set.
 */
public enum PlayerstateMessageKey implements MessageKey {

    // /god
    GOD_ON("playerstate.god.on"),
    GOD_OFF("playerstate.god.off"),
    GOD_OTHER_ON("playerstate.god.other-on"),
    GOD_OTHER_OFF("playerstate.god.other-off"),

    // /fly
    FLY_ON("playerstate.fly.on"),
    FLY_OFF("playerstate.fly.off"),
    FLY_OTHER_ON("playerstate.fly.other-on"),
    FLY_OTHER_OFF("playerstate.fly.other-off"),

    // /heal
    HEALED("playerstate.heal.self"),
    HEALED_OTHER("playerstate.heal.other"),

    // /feed
    FED("playerstate.feed.self"),
    FED_OTHER("playerstate.feed.other"),

    // /gamemode
    GAMEMODE_SET("playerstate.gamemode.self"),
    GAMEMODE_SET_OTHER("playerstate.gamemode.other"),
    GAMEMODE_INVALID("playerstate.gamemode.invalid"),

    // /speed /walkspeed /flyspeed
    SPEED_WALK_SET("playerstate.speed.walk-self"),
    SPEED_FLY_SET("playerstate.speed.fly-self"),
    SPEED_WALK_SET_OTHER("playerstate.speed.walk-other"),
    SPEED_FLY_SET_OTHER("playerstate.speed.fly-other"),
    SPEED_INVALID("playerstate.speed.invalid"),

    // /ext
    EXTINGUISHED("playerstate.extinguish.self"),
    EXTINGUISHED_OTHER("playerstate.extinguish.other"),

    // /suicide
    SUICIDE("playerstate.suicide"),

    // /near
    NEAR_HEADER("playerstate.near.header"),
    NEAR_ENTRY("playerstate.near.entry"),
    NEAR_EMPTY("playerstate.near.empty"),

    // /nightvision
    NIGHTVISION_ON("playerstate.nightvision.on"),
    NIGHTVISION_OFF("playerstate.nightvision.off"),

    // /ptime
    PTIME_SET("playerstate.ptime.set"),
    PTIME_RESET("playerstate.ptime.reset"),
    PTIME_INVALID("playerstate.ptime.invalid"),

    // /pweather
    PWEATHER_SET("playerstate.pweather.set"),
    PWEATHER_RESET("playerstate.pweather.reset"),
    PWEATHER_INVALID("playerstate.pweather.invalid");

    private final String key;

    PlayerstateMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

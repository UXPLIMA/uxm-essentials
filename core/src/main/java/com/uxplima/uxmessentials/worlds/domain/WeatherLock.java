package com.uxplima.uxmessentials.worlds.domain;

/** A locked weather state for a world; {@code NONE} leaves weather under the vanilla cycle. */
public enum WeatherLock {
    NONE,
    CLEAR,
    RAIN,
    THUNDER
}

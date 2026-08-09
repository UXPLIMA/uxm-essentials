package com.uxplima.uxmessentials.api.bukkit.event.world;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** One of a managed world's settings or gamerules was changed. */
@NullMarked
public final class UxmWorldSettingChangeEvent extends UxmWorldEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String settingKey;
    private final Optional<String> settingValue;

    public UxmWorldSettingChangeEvent(String worldName, String settingKey, Optional<String> settingValue) {
        super(worldName);
        this.settingKey = Objects.requireNonNull(settingKey, "settingKey");
        this.settingValue = Objects.requireNonNull(settingValue, "settingValue");
    }

    /** Which setting or gamerule changed. */
    public String getSettingKey() {
        return settingKey;
    }

    /** Its new value, or empty when the setting was cleared back to the server default. */
    public Optional<String> getSettingValue() {
        return settingValue;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

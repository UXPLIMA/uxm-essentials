package com.uxplima.uxmessentials.worlds.domain.event;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Published after a world's setting (property, gamerule, or spawn) changes; value absent = cleared. */
public record WorldSettingChanged(WorldName name, String settingKey, Optional<String> settingValue)
        implements WorldEvent {
    public WorldSettingChanged {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(settingKey, "settingKey");
        Objects.requireNonNull(settingValue, "settingValue");
    }
}

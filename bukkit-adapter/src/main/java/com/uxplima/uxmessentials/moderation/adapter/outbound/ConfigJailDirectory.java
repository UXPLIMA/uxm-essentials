package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.moderation.adapter.ModerationSettings;
import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link JailDirectory} over {@code moderation.conf}: a jail exists when it is in the configured
 * {@code jails} list, and its countdown mode is read from the same config. The location itself is resolved by
 * {@link BukkitSanctions} when teleporting; this directory answers only the name-validation and
 * countdown-mode questions the {@code Jail} use case asks before writing a sanction.
 */
@NullMarked
public final class ConfigJailDirectory implements JailDirectory {

    private final ModerationSettings settings;

    public ConfigJailDirectory(ModerationSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public boolean exists(String jail) {
        Objects.requireNonNull(jail, "jail");
        return settings.hasJail(jail);
    }

    @Override
    public boolean isWallClock(String jail) {
        Objects.requireNonNull(jail, "jail");
        return settings.isWallClock(jail);
    }

    @Override
    public java.util.List<String> names() {
        return settings.jailNames().stream().sorted().toList();
    }
}

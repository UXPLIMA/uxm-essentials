package com.uxplima.uxmessentials.bossbar.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.bossbar.domain.BossBarConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * {@code modules/bossbar/config.conf}, read whole and swapped in as one snapshot, so a reader sees the old file or the
 * new one and never half of each. A file that cannot be read at start leaves no bars; on a reload it fails the reload
 * and the bars already up stay.
 */
@NullMarked
public final class BossBarSettings {

    private static final String CONTENT_FILE = "config.conf";

    private final Path moduleDir;
    private final Logger log;
    private final AtomicReference<BossBarContentCodec.Parsed> parsed;

    public BossBarSettings(Path moduleDir, Logger log) {
        this.moduleDir = Objects.requireNonNull(moduleDir, "moduleDir");
        this.log = Objects.requireNonNull(log, "log");
        this.parsed = new AtomicReference<>(BossBarContentCodec.read(load(false), log));
    }

    public BossBarConfig bars() {
        return current().bars();
    }

    public List<AnimationDef> animations() {
        return current().animations();
    }

    public Duration refreshInterval() {
        return current().refreshInterval();
    }

    public boolean hiddenByDefault() {
        return current().hiddenByDefault();
    }

    private BossBarContentCodec.Parsed current() {
        return Objects.requireNonNull(parsed.get(), "parsed");
    }

    /** Read the file again and swap it in whole. */
    public void reload() {
        parsed.set(BossBarContentCodec.read(load(true), log));
    }

    private ConfigurationNode load(boolean failOnReadError) {
        Path file = moduleDir.resolve(CONTENT_FILE);
        if (!Files.exists(file)) {
            return CommentedConfigurationNode.root();
        }
        try {
            return HoconConfigurationLoader.builder().path(file).build().load();
        } catch (ConfigurateException failure) {
            log.error("failed to load " + file + "; no boss bars are shown", failure);
            if (failOnReadError) {
                throw new IllegalStateException("failed to parse boss bar config " + file, failure);
            }
            return CommentedConfigurationNode.root();
        }
    }
}

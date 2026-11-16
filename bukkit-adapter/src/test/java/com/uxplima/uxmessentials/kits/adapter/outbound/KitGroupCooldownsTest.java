package com.uxplima.uxmessentials.kits.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/** A permission-cooldowns group named by a number would also be a kit cooldown tier, so a kit refuses it. */
class KitGroupCooldownsTest {

    private final Warnings log = new Warnings();

    @Test
    void aGroupNamedByAWordIsKept() throws IOException {
        KitDefinition kit = read("""
                cooldown = 86400
                permission-cooldowns { vip = 3600 }
                """);

        assertThat(kit.permissionCooldowns()).containsEntry("vip", Duration.ofHours(1));
        assertThat(log.warnings).isEmpty();
    }

    @Test
    void aGroupNamedByANumberIsSkippedAndTheOperatorIsTold() throws IOException {
        KitDefinition kit = read("""
                cooldown = 86400
                permission-cooldowns { vip = 3600, "60" = 60 }
                """);

        assertThat(kit.permissionCooldowns()).containsOnlyKeys("vip");
        assertThat(log.warnings).hasSize(1);
    }

    private KitDefinition read(String text) throws IOException {
        ConfigurationNode node = HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(text)))
                .build()
                .load();
        return KitCodec.read("daily", node, log).orElseThrow();
    }

    /** Keeps the warnings, the lines an operator is told about a kit file. */
    private static final class Warnings implements Logger {

        final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

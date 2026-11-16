package com.uxplima.uxmessentials.kits.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
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
        assertThat(log.warnings).singleElement().asString().contains("'60'", "uxmessentials.kit.cooldown.60");
    }

    @Test
    void aSaveKeepsTheSkippedGroupInTheFile() throws IOException {
        ConfigurationNode previous = parse("""
                cooldown = 86400
                permission-cooldowns { vip = 3600, "60" = 60 }
                """);
        KitDefinition kit = KitCodec.read("daily", previous, log).orElseThrow();
        ConfigurationNode saved = CommentedConfigurationNode.root();

        KitCodec.write(saved, kit, previous);

        assertThat(saved.node("permission-cooldowns", "60").getLong()).isEqualTo(60L);
        assertThat(saved.node("permission-cooldowns", "vip").getLong()).isEqualTo(3600L);
    }

    private KitDefinition read(String text) throws IOException {
        return KitCodec.read("daily", parse(text), log).orElseThrow();
    }

    private static ConfigurationNode parse(String text) throws IOException {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(text)))
                .build()
                .load();
    }

    /** Keeps the warnings with their placeholders filled, the way an operator reads them. */
    private static final class Warnings implements Logger {

        final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            String filled = message;
            for (Object arg : args) {
                filled = filled.replaceFirst("\\{}", Matcher.quoteReplacement(String.valueOf(arg)));
            }
            warnings.add(filled);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

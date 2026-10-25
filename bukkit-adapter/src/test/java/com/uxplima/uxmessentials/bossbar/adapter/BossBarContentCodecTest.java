package com.uxplima.uxmessentials.bossbar.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.bossbar.domain.BossBarDefinition;
import com.uxplima.uxmessentials.bossbar.domain.BossBarWindow;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/** What {@code modules/bossbar/config.conf} says, read into bars. */
class BossBarContentCodecTest {

    @TempDir
    Path dir;

    private final RecordingLogger log = new RecordingLogger();

    @Test
    void readsTheBarsInTheOrderTheyAreWritten() throws IOException {
        BossBarContentCodec.Parsed parsed = read("""
                bars = [
                  { id = "website", text = "@custom.website", color = "GREEN", style = "NOTCHED_20", progress = "100" }
                  { id = "store", text = "<gold>Store", condition = "world:lobby" }
                ]
                """);

        assertThat(parsed.bars().bars()).extracting(BossBarDefinition::id).containsExactly("website", "store");
        BossBarDefinition website = parsed.bars().bars().get(0);
        assertThat(website.text()).isEqualTo("@custom.website");
        assertThat(website.color()).isEqualTo("GREEN");
        assertThat(website.style()).isEqualTo("NOTCHED_20");
        assertThat(website.progress()).isEqualTo("100");
    }

    @Test
    void aBarThatSaysLittleGetsTheDefaults() throws IOException {
        BossBarDefinition bar = read("bars = [ { id = \"plain\", text = \"hello\" } ]")
                .bars()
                .bars()
                .get(0);

        assertThat(bar.color()).isEqualTo("WHITE");
        assertThat(bar.style()).isEqualTo("PROGRESS");
        assertThat(bar.progress()).isEqualTo("100");
        assertThat(bar.window()).isEmpty();
        assertThat(bar.announcementOnly()).isFalse();
    }

    @Test
    void aTimedBarReadsItsWindowInTheServersTimeZoneAndDrainsByDefault() throws IOException {
        BossBarDefinition bar = read("""
                        time-zone = "Europe/Istanbul"
                        bars = [ { id = "event", text = "x", start = "2026-11-01 18:00", end = "2026-11-08 18:00" } ]
                        """).bars().bars().get(0);

        assertThat(bar.window())
                .contains(new BossBarWindow(
                        Optional.of(Instant.parse("2026-11-01T15:00:00Z")), Instant.parse("2026-11-08T15:00:00Z")));
        assertThat(bar.progress()).isEqualTo("time");
    }

    @Test
    void anInstantWithItsOwnOffsetIsReadAsWritten() throws IOException {
        BossBarDefinition bar = read("bars = [ { id = \"event\", text = \"x\", end = \"2026-11-08T18:00:00Z\" } ]")
                .bars()
                .bars()
                .get(0);

        assertThat(bar.window()).contains(new BossBarWindow(Optional.empty(), Instant.parse("2026-11-08T18:00:00Z")));
    }

    @Test
    void aBarThatCannotBeReadIsSkippedAndTheRestStay() throws IOException {
        BossBarContentCodec.Parsed parsed = read("""
                bars = [
                  { id = "two words", text = "x" }
                  { id = "notext" }
                  { id = "baddate", text = "x", end = "next friday" }
                  { id = "startonly", text = "x", start = "2026-11-01 18:00" }
                  { id = "backwards", text = "x", start = "2026-11-08 18:00", end = "2026-11-01 18:00" }
                  { id = "good", text = "x" }
                  { id = "good", text = "again" }
                ]
                """);

        assertThat(parsed.bars().bars()).extracting(BossBarDefinition::id).containsExactly("good");
        assertThat(parsed.bars().bars().get(0).text()).isEqualTo("x");
        assertThat(log.warnings).hasSize(6);
    }

    @Test
    void readsTheRefreshTheDefaultVisibilityAndTheAnimations() throws IOException {
        BossBarContentCodec.Parsed parsed = read("""
                refresh-ticks = 10
                hidden-by-default = true
                animations { blink { type = "FRAMES", frames = [ "a", "b" ], interval-ticks = 1 } }
                """);

        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(parsed.hiddenByDefault()).isTrue();
        assertThat(parsed.animations()).hasSize(1);
        assertThat(parsed.bars().bars()).isEmpty();
    }

    @Test
    void anEmptyFileShowsNothingAndHidesNothing() throws IOException {
        BossBarContentCodec.Parsed parsed = read("");

        assertThat(parsed.bars().bars()).isEmpty();
        assertThat(parsed.hiddenByDefault()).isFalse();
        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofSeconds(1));
    }

    private BossBarContentCodec.Parsed read(String hocon) throws IOException {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, hocon);
        ConfigurationNode root =
                HoconConfigurationLoader.builder().path(file).build().load();
        return BossBarContentCodec.read(root, log);
    }

    private static final class RecordingLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();

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

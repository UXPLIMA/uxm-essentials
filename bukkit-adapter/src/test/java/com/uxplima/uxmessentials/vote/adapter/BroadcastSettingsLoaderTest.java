package com.uxplima.uxmessentials.vote.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import net.kyori.adventure.bossbar.BossBar;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.vote.application.BroadcastSettings;
import com.uxplima.uxmessentials.vote.domain.BroadcastChannel;
import com.uxplima.uxmessentials.vote.domain.BroadcastType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Parses sample {@code broadcast} blocks and asserts the resulting {@link BroadcastSettings} and
 * {@link com.uxplima.uxmessentials.vote.adapter.outbound.BukkitVoteBroadcaster.BroadcastDisplay}: the
 * type, channel list, cooldown, and blacklist; the tolerant fallbacks for an unknown type and an unknown
 * channel/colour/overlay; and the legacy scalar-boolean back-compat (true to EVERY_VOTE, false to NONE).
 */
class BroadcastSettingsLoaderTest {

    @Test
    void parsesEveryFieldOfASectionBlock(@TempDir Path dir) throws IOException {
        BroadcastSettingsLoader.Loaded loaded = load(
                dir,
                """
                broadcast {
                  type = "COOLDOWN_PER_PLAYER"
                  cooldown-seconds = 30
                  channels = ["CHAT", "ACTION_BAR", "BOSS_BAR"]
                  title { fade-in-ms = 100, stay-ms = 1500, fade-out-ms = 200 }
                  boss-bar { color = "RED", overlay = "NOTCHED_6", seconds = 7 }
                  hidden-voters = ["StaffTester", "ANOTHER"]
                }
                """);

        BroadcastSettings settings = loaded.settings();
        assertThat(settings.type()).isEqualTo(BroadcastType.COOLDOWN_PER_PLAYER);
        assertThat(settings.cooldown()).isEqualTo(Duration.ofSeconds(30));
        assertThat(settings.channels())
                .containsExactlyInAnyOrder(
                        BroadcastChannel.CHAT, BroadcastChannel.ACTION_BAR, BroadcastChannel.BOSS_BAR);
        // Blacklist is lower-cased by the core record's compact constructor.
        assertThat(settings.blacklist()).containsExactlyInAnyOrder("stafftester", "another");

        assertThat(loaded.display().titleFadeInMs()).isEqualTo(100);
        assertThat(loaded.display().titleStayMs()).isEqualTo(1500);
        assertThat(loaded.display().titleFadeOutMs()).isEqualTo(200);
        assertThat(loaded.display().bossBarColor()).isEqualTo(BossBar.Color.RED);
        assertThat(loaded.display().bossBarOverlay()).isEqualTo(BossBar.Overlay.NOTCHED_6);
        assertThat(loaded.display().bossBarSeconds()).isEqualTo(7L);
    }

    @Test
    void unknownTypeFallsBackToEveryVote(@TempDir Path dir) throws IOException {
        BroadcastSettingsLoader.Loaded loaded = load(dir, "broadcast { type = \"NONSENSE\" }\n");

        assertThat(loaded.settings().type()).isEqualTo(BroadcastType.EVERY_VOTE);
    }

    @Test
    void allUnknownChannelsFallBackToChat(@TempDir Path dir) throws IOException {
        BroadcastSettingsLoader.Loaded onlyUnknown = load(dir, "broadcast { channels = [\"WHAT\"] }\n");

        // Every entry was unknown, so the loader falls back to the CHAT default rather than an empty set.
        assertThat(onlyUnknown.settings().channels()).containsExactly(BroadcastChannel.CHAT);
    }

    @Test
    void unknownChannelInAMixedListIsSkipped(@TempDir Path dir) throws IOException {
        BroadcastSettingsLoader.Loaded mixed = load(dir, "broadcast { channels = [\"CHAT\", \"WHAT\", \"TITLE\"] }\n");

        assertThat(mixed.settings().channels())
                .containsExactlyInAnyOrder(BroadcastChannel.CHAT, BroadcastChannel.TITLE);
    }

    @Test
    void unknownBossBarColourAndOverlayFallBackToDefaults(@TempDir Path dir) throws IOException {
        BroadcastSettingsLoader.Loaded loaded =
                load(dir, "broadcast { boss-bar { color = \"NOPE\", overlay = \"NOPE\" } }\n");

        assertThat(loaded.display().bossBarColor()).isEqualTo(BossBar.Color.PURPLE);
        assertThat(loaded.display().bossBarOverlay()).isEqualTo(BossBar.Overlay.PROGRESS);
    }

    @Test
    void legacyScalarTrueMapsToEveryVote(@TempDir Path dir) throws IOException {
        BroadcastSettingsLoader.Loaded loaded = load(dir, "broadcast = true\n");

        assertThat(loaded.settings().type()).isEqualTo(BroadcastType.EVERY_VOTE);
        assertThat(loaded.settings().channels()).containsExactly(BroadcastChannel.CHAT);
    }

    @Test
    void legacyScalarFalseMapsToNone(@TempDir Path dir) throws IOException {
        BroadcastSettingsLoader.Loaded loaded = load(dir, "broadcast = false\n");

        assertThat(loaded.settings().type()).isEqualTo(BroadcastType.NONE);
    }

    @Test
    void absentBlockYieldsEveryVoteDefaults(@TempDir Path dir) throws IOException {
        BroadcastSettingsLoader.Loaded loaded = load(dir, "enabled = true\n");

        assertThat(loaded.settings().type()).isEqualTo(BroadcastType.EVERY_VOTE);
        assertThat(loaded.settings().channels()).containsExactly(BroadcastChannel.CHAT);
        assertThat(loaded.settings().cooldown()).isEqualTo(Duration.ZERO);
        assertThat(loaded.settings().blacklist()).isEmpty();
    }

    @Test
    void absentFileYieldsDefaults(@TempDir Path dir) {
        BroadcastSettingsLoader.Loaded loaded =
                BroadcastSettingsLoader.loadFrom(dir.resolve("missing.conf"), new NoLog());

        assertThat(loaded.settings().type()).isEqualTo(BroadcastType.EVERY_VOTE);
        assertThat(loaded.display().bossBarColor()).isEqualTo(BossBar.Color.PURPLE);
    }

    private BroadcastSettingsLoader.Loaded load(Path dir, String hocon) throws IOException {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, hocon);
        return BroadcastSettingsLoader.loadFrom(file, new NoLog());
    }

    /** Swallows log output; the loader never logs on the happy path, and we do not assert on logs here. */
    private static final class NoLog implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

package com.uxplima.uxmessentials.communication.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.communication.adapter.outbound.AtomicSequenceCounter;
import com.uxplima.uxmessentials.communication.adapter.outbound.ThreadLocalRandomSource;
import com.uxplima.uxmessentials.communication.application.ResolveConnectionMessage;
import com.uxplima.uxmessentials.communication.application.ResolveJoinMessage;
import com.uxplima.uxmessentials.communication.application.ResolvedMessage;
import com.uxplima.uxmessentials.communication.domain.AnnouncerSchedule;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;
import com.uxplima.uxmessentials.communication.domain.PolicyMode;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the communication content reads identically once split across the {@code join-quit.conf},
 * {@code announcer.conf}, and {@code info-pages.conf} siblings under a module directory: the loader merges the three
 * trees at the root and feeds the same codec, so the parsed model is the same as the old monolith would have
 * produced. Also covers the absent-directory inert path and the atomic reload swap across the split files.
 */
class CommunicationSettingsSplitTest {

    @Test
    void mergesTheThreeSiblingFilesIntoOneContentModel(@TempDir Path dir) throws Exception {
        writeSplitFiles(dir);

        CommunicationSettings settings = new CommunicationSettings(dir, new NoopLogger());

        ResolvedMessage join = resolveJoin(settings, "Alice");
        assertThat(join.template()).contains("welcome Alice");
        assertThat(settings.quitPolicy().mode()).isEqualTo(PolicyMode.DEFAULT);
        assertThat(settings.deathPolicy().mode()).isEqualTo(PolicyMode.DEFAULT);
        AnnouncerSchedule announcer = settings.announcerSchedule();
        assertThat(announcer.lines()).containsExactly("tip a", "tip b");
        assertThat(announcer.interval().toSeconds()).isEqualTo(60L);
        assertThat(announcer.minOnlinePlayers()).isZero();
        assertThat(settings.firstJoinTemplate()).contains("hi {player}");
        assertThat(settings.deathInfoPage()).contains("rules");
        assertThat(settings.infoRegistry().find("rules"))
                .map(InfoPage::lines)
                .contains(List.of("Rule one", "Rule two"));
        assertThat(settings.infoRegistry().find("motd")).map(InfoPage::lines).contains(List.of("Welcome {player}"));
    }

    @Test
    void anAbsentDirectoryYieldsFullyInertContent(@TempDir Path dir) {
        Path missing = dir.resolve("does-not-exist");

        CommunicationSettings settings = new CommunicationSettings(missing, new NoopLogger());

        assertThat(settings.joinPolicy().mode()).isEqualTo(PolicyMode.DEFAULT);
        assertThat(settings.quitPolicy().mode()).isEqualTo(PolicyMode.DEFAULT);
        assertThat(settings.deathPolicy().mode()).isEqualTo(PolicyMode.DEFAULT);
        assertThat(settings.announcerSchedule().lines()).isEmpty();
        assertThat(settings.firstJoinTemplate()).isEmpty();
        assertThat(settings.deathInfoPage()).isEmpty();
        assertThat(settings.infoRegistry().isEmpty()).isTrue();
    }

    @Test
    void reloadSwapsTheAnnouncerLinesAfterRewritingTheSiblingFile(@TempDir Path dir) throws Exception {
        writeSplitFiles(dir);
        CommunicationSettings settings = new CommunicationSettings(dir, new NoopLogger());
        assertThat(settings.announcerSchedule().lines()).containsExactly("tip a", "tip b");

        Files.writeString(
                dir.resolve("announcer.conf"),
                "announcer { interval-seconds = 120, min-players = 2, ordering = SEQUENTIAL, lines = [ \"fresh tip\" ] }\n");
        settings.reload();

        assertThat(settings.announcerSchedule().lines()).containsExactly("fresh tip");
        assertThat(settings.announcerSchedule().interval().toSeconds()).isEqualTo(120L);
    }

    private static void writeSplitFiles(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("join-quit.conf"),
                """
                join { mode = CUSTOM, ordering = SEQUENTIAL, templates = [ "welcome {player}" ] }
                quit { mode = DEFAULT }
                death { mode = DEFAULT }
                first-join = "hi {player}"
                death-info-page = "rules"
                """);
        Files.writeString(
                dir.resolve("announcer.conf"),
                "announcer { interval-seconds = 60, min-players = 0, ordering = SEQUENTIAL, lines = [ \"tip a\", \"tip b\" ] }\n");
        Files.writeString(
                dir.resolve("info-pages.conf"),
                """
                info-pages {
                  rules = [ "Rule one", "Rule two" ]
                  motd = [ "Welcome {player}" ]
                }
                """);
    }

    private static ResolvedMessage resolveJoin(CommunicationSettings settings, String name) {
        ResolveConnectionMessage engine =
                new ResolveConnectionMessage(new AtomicSequenceCounter(), new ThreadLocalRandomSource());
        ResolveJoinMessage join = new ResolveJoinMessage(engine, settings::joinPolicy);
        return join.resolve(PlaceholderBindings.of(Map.of("player", name)));
    }

    private static final class NoopLogger implements Logger {
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

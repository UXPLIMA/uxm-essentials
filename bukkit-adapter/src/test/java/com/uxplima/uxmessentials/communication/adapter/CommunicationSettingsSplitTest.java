package com.uxplima.uxmessentials.communication.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.communication.adapter.outbound.AtomicSequenceCounter;
import com.uxplima.uxmessentials.communication.adapter.outbound.ThreadLocalRandomSource;
import com.uxplima.uxmessentials.communication.application.ResolveConnectionMessage;
import com.uxplima.uxmessentials.communication.application.ResolveJoinMessage;
import com.uxplima.uxmessentials.communication.application.ResolvedMessage;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
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
        AnnouncerConfig announcer = settings.announcerConfig();
        // The legacy flat lines map to one unconditional "default" CHAT announcement carrying them.
        assertThat(announcer.announcements()).singleElement().satisfies(announcement -> {
            assertThat(announcement.id()).isEqualTo("default");
            assertThat(announcement.lines()).containsExactly("tip a", "tip b");
        });
        assertThat(announcer.defaultInterval().toSeconds()).isEqualTo(60L);
        assertThat(announcer.minOnlinePlayers()).isZero();
        // The legacy bare-string first-join maps to a single-template CUSTOM welcome policy.
        assertThat(settings.firstJoinPolicy().mode()).isEqualTo(PolicyMode.CUSTOM);
        assertThat(settings.firstJoinPolicy().templates()).containsExactly("hi {player}");
        assertThat(settings.deathInfoPage()).contains("rules");
        assertThat(settings.infoRegistry().find("rules"))
                .map(InfoPage::lines)
                .contains(List.of("Rule one", "Rule two"));
        assertThat(settings.infoRegistry().find("motd")).map(InfoPage::lines).contains(List.of("Welcome {player}"));
    }

    @Test
    void parsesThePerGroupJoinOverridesAndTheMotdList(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("join-quit.conf"), """
                join {
                  mode = CUSTOM
                  ordering = SEQUENTIAL
                  templates = [ "default {player}" ]
                  groups { vip = [ "vip {player}" ] }
                }
                first-join { mode = CUSTOM, templates = [ "welcome {player}" ] }
                motd = [ "hello {player}", "have fun" ]
                """);

        CommunicationSettings settings = new CommunicationSettings(dir, new NoopLogger());

        // The default block greets an unlisted group; a listed group takes its own override (case-insensitively).
        assertThat(settings.joinPolicies().policyFor("member").templates()).containsExactly("default {player}");
        assertThat(settings.joinPolicies().policyFor("VIP").templates()).containsExactly("vip {player}");
        // The block form of first-join parses through the channel policy codec.
        assertThat(settings.firstJoinPolicy().templates()).containsExactly("welcome {player}");
        assertThat(settings.motdLines()).containsExactly("hello {player}", "have fun");
    }

    @Test
    void aPageMayDeclareAnExplicitPageSizeViaTheSectionForm(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("info-pages.conf"), """
                info-pages {
                  rules = [ "Rule one", "Rule two" ]
                  info { lines = [ "a", "b", "c" ], page-size = 2 }
                }
                """);

        CommunicationSettings settings = new CommunicationSettings(dir, new NoopLogger());

        // The bare-list form keeps the default page size; the section form honours the authored page-size.
        assertThat(settings.infoRegistry().find("rules"))
                .map(InfoPage::pageSize)
                .contains(InfoPage.DEFAULT_PAGE_SIZE);
        InfoPage info =
                settings.infoRegistry().find("info").orElseThrow(() -> new AssertionError("info page must parse"));
        assertThat(info.lines()).containsExactly("a", "b", "c");
        assertThat(info.pageSize()).isEqualTo(2);
        assertThat(info.pageCount()).isEqualTo(2);
    }

    @Test
    void anAbsentDirectoryYieldsFullyInertContent(@TempDir Path dir) {
        Path missing = dir.resolve("does-not-exist");

        CommunicationSettings settings = new CommunicationSettings(missing, new NoopLogger());

        assertThat(settings.joinPolicy().mode()).isEqualTo(PolicyMode.DEFAULT);
        assertThat(settings.quitPolicy().mode()).isEqualTo(PolicyMode.DEFAULT);
        assertThat(settings.deathPolicy().mode()).isEqualTo(PolicyMode.DEFAULT);
        assertThat(settings.announcerConfig().announcements()).isEmpty();
        assertThat(settings.firstJoinPolicy().rendersTemplate()).isFalse();
        assertThat(settings.motdLines()).isEmpty();
        assertThat(settings.deathInfoPage()).isEmpty();
        assertThat(settings.infoRegistry().isEmpty()).isTrue();
    }

    @Test
    void reloadSwapsTheAnnouncerLinesAfterRewritingTheSiblingFile(@TempDir Path dir) throws Exception {
        writeSplitFiles(dir);
        CommunicationSettings settings = new CommunicationSettings(dir, new NoopLogger());
        assertThat(firstAnnouncement(settings).lines()).containsExactly("tip a", "tip b");

        Files.writeString(
                dir.resolve("announcer.conf"),
                "announcer { interval-seconds = 120, min-players = 2, ordering = SEQUENTIAL, lines = [ \"fresh tip\" ] }\n");
        settings.reload();

        assertThat(firstAnnouncement(settings).lines()).containsExactly("fresh tip");
        assertThat(settings.announcerConfig().defaultInterval().toSeconds()).isEqualTo(120L);
    }

    @Test
    void malformedSiblingReloadKeepsTheWholePreviousContentGeneration(@TempDir Path dir) throws Exception {
        writeSplitFiles(dir);
        CommunicationSettings settings = new CommunicationSettings(dir, new NoopLogger());

        Files.writeString(dir.resolve("announcer.conf"), "announcer { lines = [ \"broken\"\n");

        assertThatThrownBy(settings::reload).isInstanceOf(IllegalStateException.class);
        assertThat(firstAnnouncement(settings).lines()).containsExactly("tip a", "tip b");
        assertThat(settings.infoRegistry().find("rules")).isPresent();
    }

    @Test
    void parsesTheRichPerAnnouncementForm(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("announcer.conf"), """
                announcer {
                  default-interval-seconds = 200
                  min-players = 3
                  ordering = SEQUENTIAL
                  announcements = [
                    { id = "tips", channels = [ "CHAT" ], lines = [ "a", "b" ] }
                    {
                      id = "vip", channels = [ "TITLE", "ACTION_BAR" ], lines = [ "vip" ]
                      condition = "permission:uxmessentials.vip", interval-seconds = 600
                      sound = "entity.player.levelup", centered = true
                    }
                  ]
                }
                """);

        CommunicationSettings settings = new CommunicationSettings(dir, new NoopLogger());
        AnnouncerConfig config = settings.announcerConfig();

        assertThat(config.defaultInterval().toSeconds()).isEqualTo(200L);
        assertThat(config.minOnlinePlayers()).isEqualTo(3);
        assertThat(config.announcements()).hasSize(2);
        Announcement tips = config.announcements().get(0);
        assertThat(tips.id()).isEqualTo("tips");
        assertThat(tips.lines()).containsExactly("a", "b");
        assertThat(tips.intervalOverride()).isEmpty();
        Announcement vip = config.announcements().get(1);
        assertThat(vip.channels())
                .containsExactlyInAnyOrder(
                        com.uxplima.uxmessentials.shared.display.BroadcastChannel.TITLE,
                        com.uxplima.uxmessentials.shared.display.BroadcastChannel.ACTION_BAR);
        assertThat(vip.intervalOverride()).map(java.time.Duration::toSeconds).contains(600L);
        assertThat(vip.sound()).contains("entity.player.levelup");
        assertThat(vip.centered()).isTrue();
        // The rotation view excludes the override announcement, leaving only the default-cadence one.
        assertThat(config.rotating().announcements())
                .extracting(Announcement::id)
                .containsExactly("tips");
    }

    private static Announcement firstAnnouncement(CommunicationSettings settings) {
        return settings.announcerConfig().announcements().stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected an announcement"));
    }

    private static void writeSplitFiles(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("join-quit.conf"), """
                join { mode = CUSTOM, ordering = SEQUENTIAL, templates = [ "welcome {player}" ] }
                quit { mode = DEFAULT }
                death { mode = DEFAULT }
                first-join = "hi {player}"
                death-info-page = "rules"
                """);
        Files.writeString(
                dir.resolve("announcer.conf"),
                "announcer { interval-seconds = 60, min-players = 0, ordering = SEQUENTIAL, lines = [ \"tip a\", \"tip b\" ] }\n");
        Files.writeString(dir.resolve("info-pages.conf"), """
                info-pages {
                  rules = [ "Rule one", "Rule two" ]
                  motd = [ "Welcome {player}" ]
                }
                """);
    }

    private static ResolvedMessage resolveJoin(CommunicationSettings settings, String name) {
        ResolveConnectionMessage engine =
                new ResolveConnectionMessage(new AtomicSequenceCounter(), new ThreadLocalRandomSource());
        ResolveJoinMessage join = new ResolveJoinMessage(engine, settings::joinPolicies, settings::firstJoinPolicy);
        return join.resolve(false, null, PlaceholderBindings.of(Map.of("player", name)));
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

package com.uxplima.uxmessentials.tablist.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.tablist.domain.TablistFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

class TablistContentCodecTest {

    @Test
    void parsesTheFormatsBlock(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(
                dir,
                """
                refresh-ticks = 40
                formats {
                  staff {
                    condition = "permission:uxmessentials.staff"
                    priority = 10
                    name-format = "<red>[Staff] {player}"
                    sort-order = 100
                    header = [ "<red>Staff online" ]
                    footer = [ "<gray>play.example.net" ]
                    world-blacklist = [ "world_the_end" ]
                  }
                  default {
                    condition = ""
                    priority = 0
                    header = [ "<gold>Welcome" ]
                  }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root);

        // 40 ticks at 50ms each is two seconds; the global cadence is read from the top-level refresh-ticks.
        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofSeconds(2L));
        assertThat(parsed.formats().formats()).hasSize(2);

        TablistFormat staff = select(parsed, "uxmessentials.staff"::equals, "world");
        assertThat(staff.name()).isEqualTo("staff");
        assertThat(staff.priority()).isEqualTo(10);
        assertThat(staff.nameFormat()).contains("<red>[Staff] {player}");
        assertThat(staff.sortOrder()).hasValue(100);
        assertThat(staff.content().header()).containsExactly("<red>Staff online");
        assertThat(staff.content().footer()).containsExactly("<gray>play.example.net");
        assertThat(staff.content().suppressedIn("world_the_end")).isTrue();

        TablistFormat fallback = select(parsed, n -> false, "world");
        assertThat(fallback.name()).isEqualTo("default");
        assertThat(fallback.nameFormat()).isEmpty();
        assertThat(fallback.sortOrder()).isEmpty();
        assertThat(fallback.content().header()).containsExactly("<gold>Welcome");
    }

    @Test
    void aFormatWithOnlyANameOverrideIsKept(@TempDir Path dir) throws Exception {
        // A format that sets neither header nor footer but does carry a name format still does something, so it must
        // survive the "drop the do-nothing format" filter.
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  vip { condition = "", priority = 0, name-format = "<gold>{player}" }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root);

        assertThat(parsed.formats().formats()).hasSize(1);
        TablistFormat vip = select(parsed, n -> true, "world");
        assertThat(vip.content().isBlank()).isTrue();
        assertThat(vip.nameFormat()).contains("<gold>{player}");
    }

    @Test
    void aNonPositiveSortOrderIsTreatedAsAbsent(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  default { condition = "", priority = 0, header = [ "x" ], sort-order = 0 }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root);

        assertThat(select(parsed, n -> true, "world").sortOrder()).isEmpty();
    }

    @Test
    void backCompatWrapsTheTopLevelTablistAsOneDefaultFormat(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(
                dir,
                """
                enabled = true
                tablist {
                  header = [ "<gold>Welcome", "<gray>%player_name%" ]
                  footer = [ "<gray>play.example.net" ]
                  refresh-ticks = 40
                  world-blacklist = [ "world_the_end" ]
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root);

        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofSeconds(2L));
        assertThat(parsed.formats().formats()).hasSize(1);
        TablistFormat only = parsed.formats().formats().get(0);
        assertThat(only.name()).isEqualTo("default");
        assertThat(only.condition()).isEqualTo(DisplayCondition.always());
        assertThat(only.priority()).isZero();
        assertThat(only.nameFormat()).isEmpty();
        assertThat(only.sortOrder()).isEmpty();
        assertThat(only.content().header()).containsExactly("<gold>Welcome", "<gray>%player_name%");
        assertThat(only.content().footer()).containsExactly("<gray>play.example.net");
        // The back-compat default matches every viewer (always-true condition).
        assertThat(parsed.formats().select(ctx(n -> false, "world"))).isPresent();
    }

    @Test
    void anEmptyRootYieldsInertContent(@TempDir Path dir) throws Exception {
        TablistContentCodec.Parsed parsed = TablistContentCodec.read(load(dir, "enabled = false\n"));

        assertThat(parsed.formats().formats()).isEmpty();
        assertThat(parsed.formats().select(ctx(n -> true, "world"))).isEmpty();
        assertThat(parsed.refreshInterval()).isPositive();
    }

    @Test
    void aBlankTablistBlockYieldsNoFormats(@TempDir Path dir) throws Exception {
        // The top-level tablist block exists but carries no header/footer; it must not produce an empty default format.
        ConfigurationNode root = load(
                dir,
                """
                tablist {
                  header = []
                  footer = []
                  refresh-ticks = 0
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root);

        assertThat(parsed.formats().formats()).isEmpty();
        // A non-positive refresh-ticks still falls back to one second rather than busy-spinning.
        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofSeconds(1L));
    }

    private static TablistFormat select(
            TablistContentCodec.Parsed parsed, java.util.function.Predicate<String> hasPermission, String world) {
        Optional<TablistFormat> selected = parsed.formats().select(ctx(hasPermission, world));
        assertThat(selected).isPresent();
        return selected.get();
    }

    private static ConditionContext ctx(java.util.function.Predicate<String> hasPermission, String world) {
        Function<String, String> resolve = s -> Map.<String, String>of().getOrDefault(s, s);
        return new ConditionContext(hasPermission, world, "SURVIVAL", resolve);
    }

    private static ConfigurationNode load(Path dir, String hocon) throws ConfigurateException, IOException {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, hocon);
        return HoconConfigurationLoader.builder().path(file).build().load();
    }
}

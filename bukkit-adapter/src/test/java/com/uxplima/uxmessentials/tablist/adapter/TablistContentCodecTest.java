package com.uxplima.uxmessentials.tablist.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.display.AnimationSpec;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.tablist.domain.TablistFiller;
import com.uxplima.uxmessentials.tablist.domain.TablistFormat;
import com.uxplima.uxmessentials.tablist.domain.TablistLayout;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

class TablistContentCodecTest {

    private static final Logger LOG = new NoopLogger();

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

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

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

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

        assertThat(parsed.formats().formats()).hasSize(1);
        TablistFormat vip = select(parsed, n -> true, "world");
        assertThat(vip.content().isBlank()).isTrue();
        assertThat(vip.nameFormat()).contains("<gold>{player}");
    }

    @Test
    void parsesAPlayerSkinSource(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  default { condition = "", priority = 0, name-format = "<gold>{player}", skin = "player:Notch" }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

        assertThat(select(parsed, n -> true, "world").skin()).contains(new TablistSkinSource.PlayerName("Notch"));
    }

    @Test
    void parsesATextureSkinSourceWithASignature(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  default { condition = "", priority = 0, header = [ "x" ], skin = "texture:dmFsdWU=:sig" }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

        assertThat(select(parsed, n -> true, "world").skin())
                .contains(new TablistSkinSource.Texture("dmFsdWU=", Optional.of("sig")));
    }

    @Test
    void aSkinOnlyFormatIsKept(@TempDir Path dir) throws Exception {
        // A format that sets neither header/footer nor a name/order but does carry a skin still does something.
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  vip { condition = "", priority = 0, skin = "player:Notch" }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

        assertThat(parsed.formats().formats()).hasSize(1);
        assertThat(select(parsed, n -> true, "world").skin()).isPresent();
    }

    @Test
    void anUnrecognisedSkinPrefixIsTreatedAsNoSkin(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  default { condition = "", priority = 0, header = [ "x" ], skin = "url:http://example" }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

        assertThat(select(parsed, n -> true, "world").skin()).isEmpty();
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

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

        assertThat(select(parsed, n -> true, "world").sortOrder()).isEmpty();
    }

    @Test
    void parsesAFillerLayout(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  default {
                    condition = ""
                    priority = 0
                    header = [ "<gold>Welcome" ]
                    layout {
                      direction = "ROWS"
                      rows = 20
                      fillers = [
                        { slot = 5, text = "<gray>play.example.net", skin = "player:Notch" }
                        { slot = 6, text = "<gold>Discord" }
                      ]
                    }
                  }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

        TablistLayout layout = select(parsed, n -> true, "world").layout();
        assertThat(layout.isEmpty()).isFalse();
        assertThat(layout.direction()).isEqualTo(TablistLayout.Direction.ROWS);
        assertThat(layout.gridRows()).isEqualTo(20);
        assertThat(layout.fillers()).hasSize(2);
        TablistFiller first = layout.fillers().get(0);
        assertThat(first.slot()).isEqualTo(5);
        assertThat(first.text()).isEqualTo("<gray>play.example.net");
        assertThat(first.skin()).contains(new TablistSkinSource.PlayerName("Notch"));
        assertThat(layout.fillers().get(1).skin()).isEmpty();
    }

    @Test
    void aFormatWithOnlyAFillerLayoutIsKept(@TempDir Path dir) throws Exception {
        // A format that sets no header/footer, name, order, or skin but does paint fillers still does something.
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  grid { condition = "", priority = 0, layout { fillers = [ { slot = 1, text = "<gold>x" } ] } }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

        assertThat(parsed.formats().formats()).hasSize(1);
        assertThat(select(parsed, n -> true, "world").layout().fillers()).hasSize(1);
    }

    @Test
    void aLayoutWithNoFillersIsEmptyAndDefaultDirection(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  default { condition = "", priority = 0, header = [ "x" ], layout { fillers = [] } }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

        TablistLayout layout = select(parsed, n -> true, "world").layout();
        assertThat(layout.isEmpty()).isTrue();
        assertThat(layout.direction()).isEqualTo(TablistLayout.Direction.COLUMNS);
    }

    @Test
    void skipsAnInvalidOrDuplicateFillerSlot(@TempDir Path dir) throws Exception {
        RecordingLogger log = new RecordingLogger();
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  default {
                    condition = ""
                    priority = 0
                    header = [ "x" ]
                    layout {
                      fillers = [
                        { slot = 0, text = "<gold>bad" }
                        { slot = 3, text = "  " }
                        { slot = 7, text = "<gold>good" }
                        { slot = 7, text = "<gray>dup" }
                      ]
                    }
                  }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, log);

        TablistLayout layout = select(parsed, n -> true, "world").layout();
        // Only the single valid, non-duplicate slot 7 survives; the bad slot, blank text, and duplicate are skipped.
        assertThat(layout.fillers()).hasSize(1);
        assertThat(layout.fillers().get(0).slot()).isEqualTo(7);
        assertThat(log.warnings).anyMatch(w -> w.contains("tablist_filler_skipped") && w.contains("invalid"));
        assertThat(log.warnings).anyMatch(w -> w.contains("tablist_filler_skipped") && w.contains("duplicate"));
    }

    @Test
    void rejectsAnOutOfGridFillerSlotButKeepsInGridSlots(@TempDir Path dir) throws Exception {
        // In ROWS mode an out-of-grid slot wraps onto an in-grid cell (raw slot 2 and 81 both map to cell 21 in a 4x20
        // grid), so two fillers would collide on one client cell. The codec bounds the slot to the grid capacity
        // (4 columns x rows) and rejects + logs the out-of-range one, exactly like the duplicate-slot guard.
        RecordingLogger log = new RecordingLogger();
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  default {
                    condition = ""
                    priority = 0
                    header = [ "x" ]
                    layout {
                      direction = "ROWS"
                      rows = 20
                      fillers = [
                        { slot = 2, text = "<gold>in-grid" }
                        { slot = 81, text = "<gold>out-of-grid" }
                      ]
                    }
                  }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, log);

        TablistLayout layout = select(parsed, n -> true, "world").layout();
        // Only the in-grid slot 2 survives; the out-of-grid slot 81 (capacity is 4x20 = 80) is dropped, not collided.
        assertThat(layout.fillers()).hasSize(1);
        assertThat(layout.fillers().get(0).slot()).isEqualTo(2);
        assertThat(log.warnings)
                .anyMatch(w ->
                        w.contains("tablist_filler_skipped") && w.contains("slot-out-of-grid") && w.contains("81"));
    }

    @Test
    void acceptsTheLastInGridSlotAndRejectsTheFirstOutOfGridSlot(@TempDir Path dir) throws Exception {
        // The boundary: slot 80 is the last cell of a 4x20 grid (kept), slot 81 is the first past it (rejected).
        RecordingLogger log = new RecordingLogger();
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  default {
                    condition = ""
                    priority = 0
                    header = [ "x" ]
                    layout {
                      rows = 20
                      fillers = [
                        { slot = 80, text = "<gold>last" }
                        { slot = 81, text = "<gold>over" }
                      ]
                    }
                  }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, log);

        TablistLayout layout = select(parsed, n -> true, "world").layout();
        assertThat(layout.fillers()).extracting(TablistFiller::slot).containsExactly(80);
    }

    @Test
    void aFormatWithNoLayoutCarriesTheEmptyLayout(@TempDir Path dir) throws Exception {
        ConfigurationNode root = load(
                dir,
                """
                formats {
                  default { condition = "", priority = 0, header = [ "<gold>Welcome" ] }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

        assertThat(select(parsed, n -> true, "world").layout().isEmpty()).isTrue();
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

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

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
        TablistContentCodec.Parsed parsed = TablistContentCodec.read(load(dir, "enabled = false\n"), LOG);

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

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, LOG);

        assertThat(parsed.formats().formats()).isEmpty();
        // A non-positive refresh-ticks still falls back to one second rather than busy-spinning.
        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofSeconds(1L));
    }

    @Test
    void parsesFramesAndScrollAnimationsAndSkipsAnUnknownType(@TempDir Path dir) throws Exception {
        // The animations block is parsed by the shared AnimationDef.parseAll, the same parse the scoreboard codec uses,
        // so the grammar (FRAMES + SCROLL + skip-unknown-with-a-log) is identical across both HUD modules.
        RecordingLogger log = new RecordingLogger();
        ConfigurationNode root = load(
                dir,
                """
                enabled = true
                animations {
                  blink { type = "FRAMES", frames = [ "ON", "OFF" ], interval-ticks = 10 }
                  marquee { type = "SCROLL", frames = [ "welcome to the server" ], interval-ticks = 2, window = 8, separator = " * " }
                  bogus { type = "WOBBLE", frames = [ "x" ] }
                }
                formats {
                  default { condition = "", priority = 0, header = [ "<gray>%anim_blink%" ] }
                }
                """);

        TablistContentCodec.Parsed parsed = TablistContentCodec.read(root, log);

        // The two valid animations are parsed; the unknown type is skipped and logged.
        assertThat(parsed.animations()).extracting(d -> d.spec().name()).containsExactlyInAnyOrder("blink", "marquee");
        AnimationDef blink = byName(parsed.animations(), "blink");
        assertThat(blink.spec().type()).isEqualTo(AnimationSpec.AnimationType.FRAMES);
        assertThat(blink.spec().frames()).containsExactly("ON", "OFF");
        assertThat(blink.spec().intervalTicks()).isEqualTo(10);
        AnimationDef marquee = byName(parsed.animations(), "marquee");
        assertThat(marquee.spec().type()).isEqualTo(AnimationSpec.AnimationType.SCROLL);
        assertThat(marquee.scroll()).hasValueSatisfying(s -> {
            assertThat(s.window()).isEqualTo(8);
            assertThat(s.separator()).isEqualTo(" * ");
        });
        assertThat(log.warnings).anyMatch(w -> w.contains("animation_skipped") && w.contains("bogus"));
    }

    private static AnimationDef byName(List<AnimationDef> defs, String name) {
        return defs.stream()
                .filter(d -> d.spec().name().equals(name))
                .findFirst()
                .orElseThrow();
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

    private static class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A logger that captures formatted {@code warn} lines so a test can assert a skip was reported. */
    private static final class RecordingLogger extends NoopLogger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void warn(String message, Object... args) {
            warnings.add(format(message, args));
        }

        private static String format(String message, Object... args) {
            String out = message;
            for (Object arg : args) {
                int at = out.indexOf("{}");
                if (at < 0) {
                    break;
                }
                out = out.substring(0, at) + arg + out.substring(at + 2);
            }
            return out;
        }
    }
}

package com.uxplima.uxmessentials.migration.convert.decentholograms;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The DecentHolograms source's golden-file suite (docs/12-migration §8). It drives the real
 * parse → map → record path over hologram files written to a temp directory in DecentHolograms' own
 * format, proving the location string, the modern {@code pages} layout and the legacy {@code lines}
 * section all map to the {@code Hologram} aggregate, and that an unknown world skips the file fail-soft.
 */
class DecentHologramsConvertTest {

    private static final WorldRef WORLD = new WorldRef(new UUID(1L, 2L), "world");
    private final WorldNameResolver worlds = name -> name.equals("world") ? Optional.of(WORLD) : Optional.empty();

    @Test
    void parsesAndMapsAModernPagedHologram(@TempDir Path dir) throws IOException {
        write(
                dir,
                "spawn.yml",
                """
                location: "world:10.5:64.0:-20.25"
                enabled: true
                display-range: 48
                update-range: 48
                pages:
                  - lines:
                      - content: "<green>Welcome"
                      - content: "to spawn"
                    actions: {}
                """);

        List<Hologram> holograms = holograms(dir);

        assertThat(holograms).hasSize(1);
        Hologram hologram = holograms.get(0);
        assertThat(hologram.name().value()).isEqualTo("spawn");
        assertThat(hologram.location().world()).isEqualTo(WORLD);
        assertThat(hologram.location().x()).isEqualTo(10.5);
        assertThat(hologram.location().y()).isEqualTo(64.0);
        assertThat(hologram.location().z()).isEqualTo(-20.25);
        assertThat(hologram.lines().stream().map(HologramLine::value)).containsExactly("<green>Welcome", "to spawn");
    }

    @Test
    void readsTheLegacyTopLevelLinesSection(@TempDir Path dir) throws IOException {
        write(
                dir,
                "old.yml",
                """
                location: "world:1:2:3"
                lines:
                  '1':
                    content: "one"
                  '2':
                    content: "two"
                """);

        assertThat(holograms(dir)).singleElement().satisfies(hologram -> assertThat(
                        hologram.lines().stream().map(HologramLine::value))
                .containsExactly("one", "two"));
    }

    @Test
    void dropsBlankSpacerLinesButKeepsTheHologram(@TempDir Path dir) throws IOException {
        write(
                dir,
                "spaced.yml",
                """
                location: "world:0:0:0"
                pages:
                  - lines:
                      - content: "header"
                      - content: "   "
                      - content: "footer"
                """);

        assertThat(holograms(dir)).singleElement().satisfies(hologram -> assertThat(
                        hologram.lines().stream().map(HologramLine::value))
                .containsExactly("header", "footer"));
    }

    @Test
    void skipsAHologramWhoseWorldTheServerDoesNotKnow(@TempDir Path dir) throws IOException {
        write(
                dir,
                "ghost.yml",
                """
                location: "nether:0:0:0"
                pages:
                  - lines:
                      - content: "x"
                """);

        assertThat(holograms(dir)).isEmpty();
    }

    @Test
    void detectsTheHologramsDirectoryAndCarriesTheId(@TempDir Path dir) {
        DecentHologramsConvert convert = new DecentHologramsConvert(worlds, dir);

        assertThat(convert.detect(dir)).isTrue();
        assertThat(convert.id()).isEqualTo(SourceId.of("decentholograms"));
    }

    private List<Hologram> holograms(Path dir) {
        DecentHologramsConvert convert = new DecentHologramsConvert(worlds, dir);
        try (ImportPlan plan = convert.plan(ImportOptions.live(dir));
                Stream<ImportRecord> records = plan.records()) {
            return records.map(record ->
                            ((ImportRecord.HologramRecord) record).hologram().hologram())
                    .toList();
        }
    }

    private static void write(Path dir, String name, String yaml) throws IOException {
        Files.writeString(dir.resolve(name), yaml);
    }
}

package com.uxplima.uxmessentials.migration.convert.fancyholograms;

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
 * The FancyHolograms source's golden-file suite (docs/12-migration §8). It drives the real
 * parse → map → record path over a {@code holograms.yml} written in FancyHolograms' own format, proving the
 * nested {@code location} block and the {@code text} list map to the {@code Hologram} aggregate, that
 * item/block holograms (no text) and unknown-world holograms are skipped fail-soft, and that a pre-v2 file
 * is refused wholesale exactly as FancyHolograms itself refuses it.
 */
class FancyHologramsConvertTest {

    private static final WorldRef WORLD = new WorldRef(new UUID(1L, 2L), "world");
    private final WorldNameResolver worlds = name -> name.equals("world") ? Optional.of(WORLD) : Optional.empty();

    @Test
    void parsesAndMapsATextHologram(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                version: 2
                holograms:
                  spawn:
                    type: TEXT
                    location:
                      world: world
                      x: 10.5
                      y: 64.0
                      z: -20.25
                    text:
                      - "<green>Welcome"
                      - "to spawn"
                """);

        List<Hologram> holograms = holograms(file, dir);

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
    void skipsItemAndBlockHologramsThatCarryNoText(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                version: 2
                holograms:
                  shop:
                    type: ITEM
                    location:
                      world: world
                      x: 1.0
                      y: 2.0
                      z: 3.0
                    item: DIAMOND
                  notice:
                    type: TEXT
                    location:
                      world: world
                      x: 4.0
                      y: 5.0
                      z: 6.0
                    text:
                      - "read me"
                """);

        assertThat(holograms(file, dir))
                .singleElement()
                .satisfies(hologram -> assertThat(hologram.name().value()).isEqualTo("notice"));
    }

    @Test
    void skipsAHologramWhoseWorldTheServerDoesNotKnow(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                version: 2
                holograms:
                  ghost:
                    type: TEXT
                    location:
                      world: nether
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    text:
                      - "x"
                """);

        assertThat(holograms(file, dir)).isEmpty();
    }

    @Test
    void refusesAPreVersionTwoFileWholesale(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                version: 1
                holograms:
                  legacy:
                    type: TEXT
                    location:
                      world: world
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    text:
                      - "old format"
                """);

        assertThat(holograms(file, dir)).isEmpty();
    }

    @Test
    void detectsTheHologramsFileAndCarriesTheId(@TempDir Path dir) throws IOException {
        Path file = write(dir, "version: 2\nholograms: {}\n");
        FancyHologramsConvert convert = new FancyHologramsConvert(worlds, file);

        assertThat(convert.detect(dir)).isTrue();
        assertThat(convert.id()).isEqualTo(SourceId.of("fancyholograms"));
    }

    private List<Hologram> holograms(Path file, Path dir) {
        FancyHologramsConvert convert = new FancyHologramsConvert(worlds, file);
        try (ImportPlan plan = convert.plan(ImportOptions.live(dir));
                Stream<ImportRecord> records = plan.records()) {
            return records.map(record ->
                            ((ImportRecord.HologramRecord) record).hologram().hologram())
                    .toList();
        }
    }

    private static Path write(Path dir, String yaml) throws IOException {
        Path file = dir.resolve("holograms.yml");
        Files.writeString(file, yaml);
        return file;
    }
}

package com.uxplima.uxmessentials.migration.essentialsx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.convert.essentialsx.map.KitMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WarpMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXKit;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXWarp;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.KitsParser;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.WarpParser;
import com.uxplima.uxmessentials.migration.convert.map.ImportedKit;
import com.uxplima.uxmessentials.migration.convert.map.ImportedWarp;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Golden-file round-trip for the EssentialsX warp and kit paths (docs/12-migration §8.2). The warp file
 * parses into a domain {@code Warp} at the expected coordinates; {@code kits.yml} parses into the expected
 * {@code KitDefinition}s with their delays and item counts.
 */
class WarpKitGoldenFileTest {

    private final WarpMapper warpMapper = new WarpMapper(fakeWorlds());
    private final KitMapper kitMapper = new KitMapper();

    @Test
    void warpFileMapsToADomainWarpAtTheExpectedLocation() throws IOException {
        EssXWarp parsed;
        try (Reader reader = open("warps/spawn.yml")) {
            parsed = new WarpParser().parse("spawn", reader);
        }
        Optional<ImportedWarp> mapped = warpMapper.map(parsed);

        assertThat(mapped).isPresent();
        assertThat(mapped.get().warp().name().value()).isEqualTo("spawn");
        assertThat(mapped.get().warp().location().x()).isEqualTo(0.5);
        assertThat(mapped.get().warp().location().world().name()).isEqualTo("world");
    }

    @Test
    void kitsFileMapsToTheExpectedDefinitions() throws IOException {
        List<EssXKit> parsed;
        try (Reader reader = open("kits.yml")) {
            parsed = new KitsParser().parse(reader);
        }
        List<ImportedKit> kits = parsed.stream().map(kitMapper::map).toList();

        assertThat(kits).extracting(k -> k.definition().id().value()).containsExactlyInAnyOrder("starter", "daily");
        ImportedKit starter = kits.stream()
                .filter(k -> k.definition().id().value().equals("starter"))
                .findFirst()
                .orElseThrow();
        assertThat(starter.definition().cooldownSeconds()).isEqualTo(30L);
        assertThat(starter.definition().items()).hasSize(3);
        assertThat(starter.definition().items().get(2).amount()).isEqualTo(16);
    }

    private static Reader open(String resource) {
        InputStream stream =
                WarpKitGoldenFileTest.class.getClassLoader().getResourceAsStream("essentialsx/" + resource);
        if (stream == null) {
            throw new IllegalStateException("missing fixture: essentialsx/" + resource);
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    private static WorldNameResolver fakeWorlds() {
        return name -> "world".equals(name)
                ? Optional.of(new WorldRef(UUID.nameUUIDFromBytes("world".getBytes(StandardCharsets.UTF_8)), "world"))
                : Optional.empty();
    }
}

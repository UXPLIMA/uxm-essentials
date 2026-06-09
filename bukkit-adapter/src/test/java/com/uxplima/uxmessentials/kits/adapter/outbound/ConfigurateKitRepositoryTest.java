package com.uxplima.uxmessentials.kits.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit coverage of the directory-backed {@link ConfigurateKitRepository}: it reads every {@code <id>.conf}
 * in the kits folder, writes one file per kit on save, drops the file on delete, splits a legacy monolith
 * {@code kits.conf} into per-kit files on first load, and skips a malformed file without failing the load.
 * No MockBukkit server is needed — the repository only parses HOCON and carries opaque Base64 item strings.
 */
class ConfigurateKitRepositoryTest {

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

    @Test
    void loadsMultiplePerKitFiles(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("starter.conf"),
                """
                cooldown = 30
                one-time = true
                cost = "0"
                items = []
                """);
        Files.writeString(
                dir.resolve("vip.conf"),
                """
                cooldown = 0
                permission = true
                cost = "100"
                items = []
                """);

        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        assertThat(repository.all()).extracting(d -> d.id().value()).containsExactlyInAnyOrder("starter", "vip");
        KitDefinition starter = repository.find(KitId.of("starter")).orElseThrow();
        assertThat(starter.cooldown()).isEqualTo(Duration.ofSeconds(30));
        assertThat(starter.oneTime()).isTrue();
        KitDefinition vip = repository.find(KitId.of("vip")).orElseThrow();
        assertThat(vip.requiresPermission()).isTrue();
        assertThat(vip.hasCost()).isTrue();
    }

    @Test
    void saveWritesASingleFile(@TempDir Path root) {
        Path dir = kitsDir(root);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        KitDefinition miner =
                KitDefinition.repeatable(KitId.of("miner"), List.of(KitItem.of("AAAA", 1)), Duration.ofSeconds(45));
        repository.save(miner);

        assertThat(Files.exists(dir.resolve("miner.conf"))).isTrue();
        assertThat(confFiles(dir)).isEqualTo(1L);
        assertThat(repository.find(KitId.of("miner"))).isPresent();

        KitRepository reloaded = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        assertThat(reloaded.find(KitId.of("miner")).orElseThrow().cooldown()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void deleteRemovesTheFile(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("gone.conf"), "cooldown = 0\nitems = []\n");
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        assertThat(repository.exists(KitId.of("gone"))).isTrue();

        repository.delete(KitId.of("gone"));

        assertThat(Files.exists(dir.resolve("gone.conf"))).isFalse();
        assertThat(repository.find(KitId.of("gone"))).isEmpty();
        assertThat(repository.all()).isEmpty();
    }

    @Test
    void legacyMonolithStillLoadsAndIsSplit(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Path legacy = legacy(root);
        Files.createDirectories(legacy.getParent());
        Files.writeString(
                legacy,
                """
                kits {
                  old {
                    cooldown = 5
                    items = []
                  }
                }
                """);

        KitRepository repository = ConfigurateKitRepository.load(dir, legacy, NOOP);

        assertThat(repository.find(KitId.of("old"))).isPresent();
        assertThat(Files.exists(dir.resolve("old.conf"))).isTrue();
        assertThat(Files.exists(legacy)).isFalse();
        assertThat(Files.exists(legacy.resolveSibling("kits.conf.migrated"))).isTrue();
    }

    @Test
    void malformedFileIsSkippedNotFatal(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("good.conf"), "cooldown = 10\nitems = []\n");
        Files.writeString(dir.resolve("broken.conf"), "cooldown = = = oops {{{\n");

        KitRepository repository = assertLoads(dir, legacy(root));

        assertThat(repository.find(KitId.of("good"))).isPresent();
    }

    @Test
    void atomicSaveOverwritesExisting(@TempDir Path root) {
        Path dir = kitsDir(root);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        KitId id = KitId.of("daily");
        repository.save(KitDefinition.repeatable(id, List.of(KitItem.of("AAAA", 1)), Duration.ofSeconds(10)));
        repository.save(KitDefinition.repeatable(id, List.of(KitItem.of("BBBB", 2)), Duration.ofSeconds(20)));

        assertThat(confFiles(dir)).isEqualTo(1L);
        KitDefinition latest = repository.find(id).orElseThrow();
        assertThat(latest.cooldown()).isEqualTo(Duration.ofSeconds(20));
        assertThat(latest.items()).containsExactly(KitItem.of("BBBB", 2));
    }

    private static KitRepository assertLoads(Path dir, Path legacy) {
        assertThatCode(() -> ConfigurateKitRepository.load(dir, legacy, NOOP)).doesNotThrowAnyException();
        return ConfigurateKitRepository.load(dir, legacy, NOOP);
    }

    private static Path kitsDir(Path root) {
        return root.resolve("modules").resolve("kits").resolve("kits");
    }

    private static Path legacy(Path root) {
        return root.resolve("kits.conf");
    }

    private static long confFiles(Path dir) {
        try (var stream = Files.newDirectoryStream(dir, "*.conf")) {
            long count = 0;
            for (Path ignored : stream) {
                count++;
            }
            return count;
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    @Test
    void savesAndLoadsStateBasedOverrides(@TempDir Path root) {
        Path dir = kitsDir(root);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        KitDefinition custom = new KitDefinition(
                KitId.of("custom"),
                List.of(KitItem.of("AAAA", 1)),
                Duration.ofSeconds(10),
                false,
                false,
                com.uxplima.uxmessentials.kits.domain.KitCost.free(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                List.of(),
                List.of(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                false,
                false,
                java.util.Optional.empty(),
                java.math.BigDecimal.ZERO,
                "default",
                java.util.Map.of(),
                0,
                java.util.Optional.of("BARRIER"),
                java.util.Optional.of("<red>No Perm"),
                List.of("Lore line 1"),
                java.util.Optional.of("CLOCK"),
                java.util.Optional.of("<yellow>Cooldown"),
                List.of("Cooldown lore"),
                java.util.Optional.of("MINECART"),
                java.util.Optional.of("<red>Claimed"),
                List.of("Claimed lore"),
                java.util.Optional.of("GOLD_NUGGET"),
                java.util.Optional.of("<red>Cannot Afford"),
                List.of("Price is %cost%"));

        repository.save(custom);

        KitRepository reloaded = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        KitDefinition loaded = reloaded.find(KitId.of("custom")).orElseThrow();

        assertThat(loaded.noPermissionMaterial()).contains("BARRIER");
        assertThat(loaded.noPermissionName()).contains("<red>No Perm");
        assertThat(loaded.noPermissionLore()).containsExactly("Lore line 1");

        assertThat(loaded.cooldownMaterial()).contains("CLOCK");
        assertThat(loaded.cooldownName()).contains("<yellow>Cooldown");
        assertThat(loaded.cooldownLore()).containsExactly("Cooldown lore");

        assertThat(loaded.claimedMaterial()).contains("MINECART");
        assertThat(loaded.claimedName()).contains("<red>Claimed");
        assertThat(loaded.claimedLore()).containsExactly("Claimed lore");

        assertThat(loaded.unaffordableMaterial()).contains("GOLD_NUGGET");
        assertThat(loaded.unaffordableName()).contains("<red>Cannot Afford");
        assertThat(loaded.unaffordableLore()).containsExactly("Price is %cost%");
    }
}

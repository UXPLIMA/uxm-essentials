package com.uxplima.uxmessentials.guard.adapter.outbound;

import com.uxplima.uxmessentials.guard.application.port.GuardBackups;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.persistence.guard.RegionJson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Region backups as JSON files under {@code plugins/uxmEssentials/guard/backups}. A file is written to a temporary
 * name and moved into place, so a crash never leaves half a backup; the oldest files beyond the kept count go.
 */
public final class FileGuardBackups implements GuardBackups {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Path folder;
    private final Clock clock;

    public FileGuardBackups(Path folder, Clock clock) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String write(List<Region> regions, String reason, int keep) {
        String safeReason = reason.replaceAll("[^A-Za-z0-9-]", "");
        String name = STAMP.format(clock.instant()) + "-" + safeReason + ".json";
        try {
            Files.createDirectories(folder);
            Path temp = folder.resolve(name + ".tmp");
            Files.writeString(temp, RegionJson.writeAll(regions), StandardCharsets.UTF_8);
            Files.move(temp, folder.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            List<String> all = list();
            for (int i = keep; i < all.size(); i++) {
                Files.deleteIfExists(folder.resolve(all.get(i)));
            }
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException("guard backup could not be written", e);
        }
    }

    @Override
    public List<Region> read(String name) {
        Path file = folder.resolve(name).normalize();
        if (!file.startsWith(folder.normalize())) {
            throw new IllegalArgumentException("not a backup: " + name);
        }
        try {
            return RegionJson.readAll(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("guard backup could not be read", e);
        }
    }

    @Override
    public List<String> list() {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("guard backups could not be listed", e);
        }
    }
}

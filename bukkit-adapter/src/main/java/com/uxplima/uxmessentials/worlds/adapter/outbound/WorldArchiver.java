package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.jspecify.annotations.NullMarked;

/**
 * Pure-filesystem zip/unzip helper for the world backup/restore flow. Deflates a world folder into a
 * single archive and inflates it back, with a security-critical zip-slip guard so a crafted archive
 * cannot write outside the chosen target directory. No Bukkit here: the backup use cases call this
 * through the {@code Scheduler}'s async context.
 */
@NullMarked
public final class WorldArchiver {

    /** Streams every regular file under {@code sourceDir} into {@code targetZip}, creating parents. */
    public void zip(Path sourceDir, Path targetZip) throws IOException {
        Objects.requireNonNull(sourceDir, "sourceDir");
        Objects.requireNonNull(targetZip, "targetZip");
        Path parent = targetZip.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(targetZip));
                Stream<Path> tree = Files.walk(sourceDir)) {
            for (Path file : (Iterable<Path>) tree.filter(Files::isRegularFile)::iterator) {
                addFile(zos, sourceDir, file);
            }
        }
    }

    private void addFile(ZipOutputStream zos, Path sourceDir, Path file) throws IOException {
        String entryName = sourceDir.relativize(file).toString().replace(File.separatorChar, '/');
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zos); // streams the file rather than buffering it whole
        zos.closeEntry();
    }

    /** Inflates {@code sourceZip} into {@code targetDir}, refusing any entry that escapes it. */
    public void unzip(Path sourceZip, Path targetDir) throws IOException {
        Objects.requireNonNull(sourceZip, "sourceZip");
        Objects.requireNonNull(targetDir, "targetDir");
        Files.createDirectories(targetDir);
        Path normalizedTarget = targetDir.normalize();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(sourceZip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                extractEntry(zis, entry, targetDir, normalizedTarget);
                zis.closeEntry();
            }
        }
    }

    private void extractEntry(ZipInputStream zis, ZipEntry entry, Path targetDir, Path normalizedTarget)
            throws IOException {
        Path resolved = targetDir.resolve(entry.getName()).normalize();
        if (!resolved.startsWith(normalizedTarget)) {
            throw new IOException("blocked zip-slip path traversal: " + entry.getName());
        }
        if (Files.isSymbolicLink(resolved)) {
            throw new IOException("refusing to follow symlink: " + entry.getName());
        }
        if (entry.isDirectory()) {
            Files.createDirectories(resolved);
            return;
        }
        Path parent = resolved.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING); // streams the entry
    }

    /** Recursively deletes {@code dir} (deepest-first), propagating the first I/O failure. */
    public void deleteTree(Path dir) throws IOException {
        Objects.requireNonNull(dir, "dir");
        if (!Files.exists(dir)) {
            return;
        }
        List<Path> ordered;
        try (Stream<Path> paths = Files.walk(dir)) {
            ordered = paths.sorted(Comparator.reverseOrder()).toList();
        }
        IOException failure = null;
        for (Path path : ordered) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}

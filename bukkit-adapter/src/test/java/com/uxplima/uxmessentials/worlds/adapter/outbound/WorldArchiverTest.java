package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldArchiverTest {

    private final WorldArchiver archiver = new WorldArchiver();

    @Test
    void zipThenUnzipReproducesTheTreeByteForByte(@TempDir Path tmp) throws IOException {
        Path source = tmp.resolve("source");
        byte[] rootBytes = "root-contents".getBytes(StandardCharsets.UTF_8);
        byte[] subBytes = new byte[] {0, 1, 2, 3, 4, 5};
        byte[] deepBytes = "deeply-nested".getBytes(StandardCharsets.UTF_8);
        writeFile(source.resolve("a.txt"), rootBytes);
        writeFile(source.resolve("sub/b.txt"), subBytes);
        writeFile(source.resolve("sub/deep/c.txt"), deepBytes);

        Path zip = tmp.resolve("out/archive.zip");
        archiver.zip(source, zip);
        assertThat(Files.isRegularFile(zip)).isTrue();

        Path dest = tmp.resolve("dest");
        archiver.unzip(zip, dest);

        assertThat(Files.readAllBytes(dest.resolve("a.txt"))).isEqualTo(rootBytes);
        assertThat(Files.readAllBytes(dest.resolve("sub/b.txt"))).isEqualTo(subBytes);
        assertThat(Files.readAllBytes(dest.resolve("sub/deep/c.txt"))).isEqualTo(deepBytes);

        long extracted;
        try (var paths = Files.walk(dest)) {
            extracted = paths.filter(Files::isRegularFile).count();
        }
        assertThat(extracted).isEqualTo(3); // exactly the three files, no extras
    }

    @Test
    void unzipRejectsAndWritesNothingForAZipSlipEntry(@TempDir Path tmp) throws IOException {
        // Hand-craft a malicious archive whose entry name escapes the target via "../".
        Path malicious = tmp.resolve("evil.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(malicious))) {
            zos.putNextEntry(new ZipEntry("ok.txt"));
            zos.write("harmless".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("../escape.txt"));
            zos.write("pwned".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        Path target = tmp.resolve("target");

        assertThatThrownBy(() -> archiver.unzip(malicious, target)).isInstanceOf(IOException.class);
        // The escaped sibling location (target's parent) must be untouched.
        assertThat(Files.exists(tmp.resolve("escape.txt"))).isFalse();
    }

    @Test
    void emptySourceProducesAValidArchiveThatUnzipsToAnEmptyDir(@TempDir Path tmp) throws IOException {
        Path source = tmp.resolve("empty");
        Files.createDirectories(source);

        Path zip = tmp.resolve("empty.zip");
        archiver.zip(source, zip);
        assertThat(Files.isRegularFile(zip)).isTrue();

        Path dest = tmp.resolve("empty-dest");
        archiver.unzip(zip, dest);
        assertThat(Files.isDirectory(dest)).isTrue();
        try (var paths = Files.list(dest)) {
            assertThat(paths.count()).isZero();
        }
    }

    @Test
    void deleteTreeRemovesAPopulatedTreeAndNoOpsOnMissing(@TempDir Path tmp) throws IOException {
        Path tree = tmp.resolve("tree");
        writeFile(tree.resolve("x.txt"), "x".getBytes(StandardCharsets.UTF_8));
        writeFile(tree.resolve("nested/y.txt"), "y".getBytes(StandardCharsets.UTF_8));

        archiver.deleteTree(tree);
        assertThat(Files.exists(tree)).isFalse();

        archiver.deleteTree(tmp.resolve("does-not-exist")); // no-op, no throw
    }

    private static void writeFile(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(bytes);
        }
    }
}

package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The uid a world folder records, read without loading the world. */
class WorldUidFilesTest {

    /**
     * The decompressed {@code data/paper/metadata.dat} a Paper 26.2 server wrote for its overworld: a root compound
     * holding {@code DataVersion} and {@code data.uuid}.
     */
    private static final String METADATA = "0a000003000b4461746156657273696f6e000013270a0004646174610b0004757569640000"
            + "0004b86fce70f5184b55bfc91767fb9d10fb0000";

    private static final UUID RECORDED = UUID.fromString("b86fce70-f518-4b55-bfc9-1767fb9d10fb");

    @TempDir
    Path folder;

    @Test
    void aDimensionFolderOfThisServerVersionAnswersTheUidInItsMetadata() throws IOException {
        Path paper = Files.createDirectories(folder.resolve("data").resolve("paper"));
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(paper.resolve("metadata.dat")))) {
            out.write(HexFormat.of().parseHex(METADATA));
        }

        assertThat(WorldUidFiles.read(folder)).contains(RECORDED);
    }

    @Test
    void aWorldFolderCopiedFromAnOlderServerAnswersTheUidInItsUidFile() throws IOException {
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(folder.resolve("uid.dat")))) {
            out.writeLong(RECORDED.getMostSignificantBits());
            out.writeLong(RECORDED.getLeastSignificantBits());
        }

        assertThat(WorldUidFiles.read(folder)).contains(RECORDED);
    }

    @Test
    void aFolderThatRecordsNoUidOrADamagedOneAnswersNothing() throws IOException {
        assertThat(WorldUidFiles.read(folder)).isEmpty();

        Path paper = Files.createDirectories(folder.resolve("data").resolve("paper"));
        Files.write(paper.resolve("metadata.dat"), new byte[] {1, 2, 3});

        assertThat(WorldUidFiles.read(folder)).isEmpty();
    }
}

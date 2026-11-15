package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The uid a world folder records, read without loading the world. */
class WorldUidFilesTest {

    /** The uid the Paper 26.2 server whose overworld wrote {@code worlds/paper-26.2-metadata.dat} gave it. */
    private static final UUID RECORDED = UUID.fromString("b86fce70-f518-4b55-bfc9-1767fb9d10fb");

    @TempDir
    Path folder;

    @Test
    void aDimensionFolderOfThisServerVersionAnswersTheUidInItsMetadata() throws IOException {
        // Copied byte for byte from a Paper 26.2 server's world/dimensions/minecraft/overworld/data/paper.
        Path paper = Files.createDirectories(folder.resolve("data").resolve("paper"));
        try (InputStream real = Objects.requireNonNull(
                WorldUidFilesTest.class.getResourceAsStream("/worlds/paper-26.2-metadata.dat"), "metadata fixture")) {
            Files.copy(real, paper.resolve("metadata.dat"));
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

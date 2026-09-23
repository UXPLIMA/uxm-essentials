package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.Server;
import org.bukkit.World;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Where a world keeps its files on Paper 26.2.
 *
 * <p>A world is no longer a folder beside the server jar. On a Paper 26.2 scratch server on 2026-09-23 the overworld
 * was at {@code world/dimensions/minecraft/overworld}, and a world {@code /worlds create probeworld} made was at
 * {@code world/dimensions/minecraft/probeworld}, with the one {@code level.dat} at {@code world/}. Every path here was
 * {@code <container>/<name>}, so a backup of that world failed with no such file, a delete answered that no such folder
 * was on disk while it was, and a restore would have written the archive where nothing reads it. uxm-plots found the
 * same layout for its own worlds.
 */
final class WorldFoldersTest {

    @TempDir
    Path container;

    private Server server;

    @BeforeEach
    void setUp() throws IOException {
        // WorldCreator reads its key through the server's unsafe values, which MockBukkit answers.
        MockBukkit.mock();
        server = mock(Server.class);
        when(server.getWorldContainer()).thenReturn(container.toFile());
        when(server.getLevelDirectory()).thenReturn(container.resolve("world"));
        World overworld = mock(World.class);
        when(overworld.getName()).thenReturn("world");
        when(overworld.getWorldFolder())
                .thenReturn(container
                        .resolve("world/dimensions/minecraft/overworld")
                        .toFile());
        when(server.getWorlds()).thenReturn(List.of(overworld));
        when(server.getWorld("world")).thenReturn(overworld);
        Files.createDirectories(container.resolve("world/dimensions/minecraft/overworld/region"));
        Files.createDirectories(container.resolve("world/dimensions/minecraft/the_nether/region"));
        Files.createDirectories(container.resolve("world/dimensions/minecraft/probeworld/region"));
        Files.createFile(container.resolve("world/level.dat"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a world named with a space is found under the key the server gives it")
    void aSpaceBecomesTheServersKey() throws IOException {
        Files.createDirectories(container.resolve("world/dimensions/minecraft/my_arena/region"));

        assertThat(new WorldFolders(server).of("My Arena"))
                .isEqualTo(container.resolve("world/dimensions/minecraft/my_arena"));
    }

    @Test
    @DisplayName("a world that is not loaded is found under the level's dimensions")
    void anUnloadedWorldIsUnderTheDimensions() {
        WorldFolders folders = new WorldFolders(server);

        assertThat(folders.of("probeworld")).isEqualTo(container.resolve("world/dimensions/minecraft/probeworld"));
        assertThat(folders.exists("probeworld")).isTrue();
    }

    @Test
    @DisplayName("a loaded world answers with the folder the server says it uses")
    void aLoadedWorldIsWhereTheServerSays() {
        assertThat(new WorldFolders(server).of("world"))
                .isEqualTo(container.resolve("world/dimensions/minecraft/overworld"));
    }

    @Test
    @DisplayName("a world a player named in capitals is found under its lower-case key")
    void aNameIsItsLowerCaseKey() {
        assertThat(new WorldFolders(server).of("ProbeWorld"))
                .isEqualTo(container.resolve("world/dimensions/minecraft/probeworld"));
    }

    @Test
    @DisplayName("a world copied in the old way, beside the server, is still found")
    void anOldFolderIsStillFound() throws IOException {
        Files.createDirectories(container.resolve("spawnmap"));
        Files.createFile(container.resolve("spawnmap/level.dat"));

        WorldFolders folders = new WorldFolders(server);

        assertThat(folders.of("spawnmap")).isEqualTo(container.resolve("spawnmap"));
        assertThat(folders.exists("spawnmap")).isTrue();
    }

    @Test
    @DisplayName("a world that is nowhere is answered with where a new one would go, and does not exist")
    void aMissingWorldGoesUnderTheDimensions() {
        WorldFolders folders = new WorldFolders(server);

        assertThat(folders.of("nowhere")).isEqualTo(container.resolve("world/dimensions/minecraft/nowhere"));
        assertThat(folders.exists("nowhere")).isFalse();
    }

    @Test
    @DisplayName("the worlds on disk are named the way the server names them, old folders included")
    void theWorldsOnDiskAreNamedAsTheServerNamesThem() throws IOException {
        Files.createDirectories(container.resolve("spawnmap"));
        Files.createFile(container.resolve("spawnmap/level.dat"));

        assertThat(new WorldFolders(server).onDisk())
                .containsExactlyInAnyOrder("world", "world_nether", "probeworld", "spawnmap");
    }

    /** A name is a world's name and never a path: the old check refused one that climbed out of the container. */
    @Test
    @DisplayName("a name that is a path names no world")
    void aPathIsNoWorld() throws IOException {
        Files.createDirectories(container.resolve("outside"));
        WorldFolders folders = new WorldFolders(server);

        assertThat(folders.exists("../outside")).isFalse();
        assertThat(folders.exists("world/dimensions")).isFalse();
        assertThat(folders.exists("..")).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> folders.of("../outside"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

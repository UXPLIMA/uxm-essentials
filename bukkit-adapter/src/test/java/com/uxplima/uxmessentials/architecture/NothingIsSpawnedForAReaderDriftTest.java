package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * What a player only reads is drawn for each of them, never spawned for everybody (ADR 0011).
 *
 * <p>A text display spawned for words is an entity the world keeps, loses to a chunk unload or leaves behind after a
 * crash, and it reads in one language for everybody standing near it. The overlay that sent each viewer their own
 * words on top of one needed a language watch of the plugin's own to stay current, and so did an item renamed in
 * its holder's language when it was handed out. The library's packet hologram and item view draw per viewer and
 * follow a language on their own, so none of those may come back here.
 */
class NothingIsSpawnedForAReaderDriftTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** What may not appear in production code, each with what to use instead. */
    private static final List<List<String>> FORBIDDEN = List.of(
            List.of("TextDisplay.class", "a text display spawned for words: send a PacketHologram"),
            List.of("Holograms.spawn(", "a spawned hologram: send a PacketHologram"),
            List.of("Holograms.builder()", "a spawned hologram: send a PacketHologram"),
            List.of("com.uxplima.uxmlib.packet.display.ViewerText", "an overlay on a spawned display"),
            List.of("com.uxplima.uxmlib.hologram.PerViewerHologram", "a spawned hologram per viewer"),
            List.of(
                    "com.uxplima.uxmlib.text.message.LanguageWatch",
                    "a language watch of the plugin's own: ItemViews and PacketHologram follow the language"));

    @Test
    void nothingIsSpawnedOrWatchedForAReader() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = Files.readString(file, StandardCharsets.UTF_8)
                        .replaceAll("(?s)/\\*.*?\\*/", "")
                        .replaceAll("//[^\\n]*", "");
                for (List<String> rule : FORBIDDEN) {
                    if (code.contains(rule.get(0))) {
                        found.add(file.getFileName() + " names " + rule.get(0) + ": " + rule.get(1));
                    }
                }
            }
        }

        assertThat(found)
                .describedAs("words a player reads are drawn for each viewer by the library, never spawned or"
                        + " watched by this plugin")
                .isEmpty();
    }
}

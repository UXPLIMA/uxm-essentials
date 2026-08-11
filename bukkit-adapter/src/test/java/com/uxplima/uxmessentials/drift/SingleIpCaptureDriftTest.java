package com.uxplima.uxmessentials.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Locks the one-capture invariant behind alt detection. Two contexts used to write their own record of "which
 * account connected from which address" on the same join: security stored a keyed token, moderation stored the
 * raw address, and the two answered {@code /ipalts} and {@code /alts} from different tables. The consolidation
 * left a single kernel capture ({@code IpHistoryRecorder}) writing a single table ({@code ip_history}), which
 * every alt read then queries by token.
 *
 * <p>Two things can silently undo that, so both are asserted here. A context could start writing its own
 * associations again: production code may call {@code IpHistoryStore#record} from the recorder alone. Or the
 * legacy moderation table could be read back into service after the one-shot move: production code may name
 * {@code MODERATION_IP_HISTORY} only in the backfill that empties it.
 */
class SingleIpCaptureDriftTest {

    private static final List<String> MAIN_SOURCE_ROOTS = List.of(
            "core/src/main/java",
            "bukkit-adapter/src/main/java",
            "persistence-adapter/src/main/java",
            "velocity-adapter/src/main/java",
            "discord-adapter/src/main/java");

    private static final String STORE_IMPORT = "com.uxplima.uxmessentials.shared.application.port.IpHistoryStore";
    private static final String RECORDER = "IpHistoryRecorder.java";
    private static final String BACKFILL = "LegacyIpHistoryBackfill.java";

    // A field, parameter or local declared as the port: `IpHistoryStore store` in any of the three positions.
    private static final Pattern HOLDER = Pattern.compile("\\bIpHistoryStore\\s+(\\w+)\\b");

    @Test
    void onlyTheKernelRecorderWritesAnAssociation() {
        List<String> writers = productionSources()
                .filter(file -> body(file).contains(STORE_IMPORT))
                .filter(SingleIpCaptureDriftTest::callsRecord)
                .map(file -> file.getFileName().toString())
                .sorted()
                .toList();

        assertThat(writers)
                .as("a join's IP association is captured in exactly one place, so no read can race a second writer")
                .containsExactly(RECORDER);
    }

    @Test
    void everyOtherConsumerOfTheHistoryOnlyReadsIt() {
        List<String> readers = productionSources()
                .filter(file -> body(file).contains(STORE_IMPORT))
                .map(file -> file.getFileName().toString())
                .sorted()
                .toList();

        // The store is genuinely shared: security's guard and lookups, moderation's alt and ban reads, the shared
        // address lookup and the wiring all hold one. The anti-rot check is that they are plural and read-only.
        assertThat(readers).hasSizeGreaterThan(5).contains(RECORDER);
    }

    @Test
    void theLegacyModerationTableIsNamedOnlyByTheBackfillThatEmptiesIt() {
        List<String> namers = productionSources()
                .filter(file -> body(file).contains("MODERATION_IP_HISTORY"))
                .map(file -> file.getFileName().toString())
                .sorted()
                .toList();

        assertThat(namers)
                .as("the pre-consolidation raw-address table is only ever drained, never read or written again")
                .containsExactly(BACKFILL);
    }

    /**
     * True when the file calls {@code record(...)} on a holder it declared as an {@code IpHistoryStore}, which is
     * the port's only write. Resolving the receiver from its declaration keeps an unrelated {@code record(...)} on
     * another type (the name index, say) out of the result.
     */
    private static boolean callsRecord(Path file) {
        String body = body(file);
        Matcher holders = HOLDER.matcher(body);
        while (holders.find()) {
            if (body.contains(holders.group(1) + ".record(")) {
                return true;
            }
        }
        return false;
    }

    private static Stream<Path> productionSources() {
        Path root = repoRoot();
        return MAIN_SOURCE_ROOTS.stream()
                .map(root::resolve)
                .filter(Files::isDirectory)
                .flatMap(dir -> javaFiles(dir).stream());
    }

    private static List<Path> javaFiles(Path dir) {
        try (Stream<Path> tree = Files.walk(dir)) {
            return tree.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to walk " + dir, failure);
        }
    }

    private static String body(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + file, failure);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}

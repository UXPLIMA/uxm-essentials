package com.uxplima.uxmessentials.shared.adapter.outbound.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every piece of scheduled work goes through the Scheduler port, so it lands on the thread Folia expects. A Bukkit
 * scheduler or a raw thread reached for directly works on Paper and breaks on Folia, which no other test notices,
 * so the main sources of every module are read for one.
 */
class SchedulingThroughThePortTest {

    /** The adapter that implements the port on Paper's schedulers, the one place they belong. */
    private static final Set<String> ALLOWED =
            Set.of("bukkit-adapter/src/main/java/com/uxplima/uxmessentials/shared/adapter/outbound/scheduler/"
                    + "FoliaScheduler.java");

    private static final Pattern DIRECT = Pattern.compile("\\.getScheduler\\s*\\(\\s*\\)"
            + "|\\bgetAsyncScheduler\\s*\\("
            + "|\\bgetGlobalRegionScheduler\\s*\\("
            + "|\\bgetRegionScheduler\\s*\\("
            + "|\\bBukkitRunnable\\b"
            + "|\\bBukkitScheduler\\b"
            + "|\\bnew\\s+Thread\\s*\\(");

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    @Test
    void noMainSourceSchedulesAroundThePort() throws IOException {
        Path root = repositoryRoot();
        List<String> found = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path sources = module.resolve("src/main/java");
                if (!Files.isDirectory(sources)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(sources)) {
                    for (Path file :
                            files.filter(f -> f.toString().endsWith(".java")).toList()) {
                        scanned++;
                        String relative = root.relativize(file).toString().replace('\\', '/');
                        if (!ALLOWED.contains(relative)) {
                            found.addAll(directCalls(relative, file));
                        }
                    }
                }
            }
        }

        assertThat(scanned).as("the scan read the main sources").isGreaterThan(100);
        assertThat(found)
                .as("schedule through the Scheduler port instead of a Bukkit scheduler or a raw thread")
                .isEmpty();
    }

    @Test
    void theAllowedAdapterStillReachesPapersSchedulers() {
        Path adapter = repositoryRoot().resolve(ALLOWED.iterator().next());

        assertThat(directCalls("adapter", adapter))
                .as("the pattern still matches the calls the adapter makes, so an empty scan means something")
                .isNotEmpty();
    }

    private static List<String> directCalls(String name, Path file) {
        String code;
        try {
            code = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Comments may name the schedulers to say why they are not used; only code counts.
        // Newlines inside a block comment are kept, so the reported line numbers stay right.
        code = BLOCK_COMMENT
                .matcher(code)
                .replaceAll(comment -> Matcher.quoteReplacement(comment.group().replaceAll("[^\\n]", " ")));
        code = LINE_COMMENT.matcher(code).replaceAll("");
        List<String> hits = new ArrayList<>();
        Matcher matcher = DIRECT.matcher(code);
        while (matcher.find()) {
            long line = code.substring(0, matcher.start())
                            .chars()
                            .filter(c -> c == '\n')
                            .count()
                    + 1;
            hits.add(name + ":" + line + " " + matcher.group());
        }
        return hits;
    }

    private static Path repositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("settings.gradle.kts"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException(
                    "no settings.gradle.kts above " + Path.of("").toAbsolutePath());
        }
        return dir;
    }
}

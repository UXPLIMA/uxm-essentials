package com.uxplima.uxmessentials.shared.adapter.outbound.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every piece of scheduled work goes through the Scheduler port, so it lands on the thread Folia expects. A Bukkit
 * scheduler, a raw thread or a private pool reached for directly works on Paper and breaks on Folia, which no other
 * test notices, so the main sources of every module are read for one.
 */
class SchedulingThroughThePortTest {

    private static final String FOLIA_SCHEDULER =
            "bukkit-adapter/src/main/java/com/uxplima/uxmessentials/shared/adapter/outbound/scheduler/"
                    + "FoliaScheduler.java";

    /** The files allowed their own threads, each with the reason it owns them. */
    private static final Map<String, String> ALLOWED = Map.of(
            FOLIA_SCHEDULER,
            "implements the port on Paper's schedulers",
            "rest-adapter/src/main/java/com/uxplima/uxmessentials/rest/http/RestServer.java",
            "the HTTP server's connection threads, which never touch the Bukkit API",
            "rest-adapter/src/main/java/com/uxplima/uxmessentials/rest/bridge/EventBridge.java",
            "the HTTP event stream's sender thread, which never touches the Bukkit API",
            "migration/src/main/java/com/uxplima/uxmessentials/migration/ImportExecutor.java",
            "the bounded database writer pool that caps import write pressure");

    private static final Pattern DIRECT = Pattern.compile("\\bgetScheduler\\b"
            + "|\\bgetAsyncScheduler\\b"
            + "|\\bgetGlobalRegionScheduler\\b"
            + "|\\bgetRegionScheduler\\b"
            + "|\\bBukkitRunnable\\b"
            + "|\\bBukkitScheduler\\b"
            + "|\\bnew\\s+Thread\\s*\\("
            + "|\\bThread\\s*\\.\\s*of(?:Virtual|Platform)\\s*\\("
            + "|\\bExecutors\\s*\\.\\s*new\\w*\\s*\\("
            + "|\\bnew\\s+(?:Scheduled)?ThreadPoolExecutor\\s*\\("
            + "|\\bnew\\s+(?:java\\.util\\.)?Timer\\s*\\(");

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
                        if (!ALLOWED.containsKey(relative)) {
                            found.addAll(directCalls(relative, read(file)));
                        }
                    }
                }
            }
        }

        assertThat(scanned).as("the scan read the main sources").isGreaterThan(100);
        assertThat(found)
                .as("schedule through the Scheduler port instead of a Bukkit scheduler, a raw thread or a pool")
                .isEmpty();
    }

    @Test
    void theAllowedAdapterStillReachesPapersSchedulers() {
        assertThat(directCalls("adapter", read(repositoryRoot().resolve(FOLIA_SCHEDULER))))
                .as("the pattern still matches the calls the adapter makes, so an empty scan means something")
                .isNotEmpty();
    }

    @Test
    void everyShapeOfADirectCallIsCaught() {
        String code = """
                import static org.bukkit.Bukkit.getAsyncScheduler;
                class Sample {
                    void run(Player player) {
                        getAsyncScheduler().runNow(plugin, task -> {});
                        Function<Player, EntityScheduler> scheduler = Player::getScheduler;
                        String url = "https://example.invalid"; new Thread(this::work).start();
                        var pool = Executors.newFixedThreadPool(2);
                        var timer = new Timer();
                        Thread.ofVirtual().start(this::work);
                    }
                }
                """;

        assertThat(directCalls("sample", code))
                .containsExactly(
                        "sample:1 getAsyncScheduler",
                        "sample:4 getAsyncScheduler",
                        "sample:5 getScheduler",
                        "sample:6 new Thread(",
                        "sample:7 Executors.newFixedThreadPool(",
                        "sample:8 new Timer(",
                        "sample:9 Thread.ofVirtual(");
    }

    @Test
    void commentsAndStringsDoNotCount() {
        String code = """
                class Sample {
                    // never Bukkit.getScheduler(), see the port
                    /* nor new Thread(...) */
                    String why = "not getAsyncScheduler()";
                    char quote = '"'; int after = 1;
                }
                """;

        assertThat(directCalls("sample", code)).isEmpty();
    }

    private static List<String> directCalls(String name, String source) {
        String code = codeOnly(source);
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

    /**
     * The source with every comment and every string, text block and char literal blanked out, newlines kept so the
     * line numbers stay right. Comments may name the schedulers to say why they are not used, and a {@code //} inside
     * a string is not a comment, so a plain pattern cannot tell the two apart.
     */
    private static String codeOnly(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                i = blank(source, i, stop, out);
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                while (end > 0 && source.charAt(end - 1) == '\\') {
                    end = source.indexOf("\"\"\"", end + 1);
                }
                int stop = end < 0 ? n : end + 3;
                i = blank(source, i, stop, out);
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && source.charAt(j) != c && source.charAt(j) != '\n') {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                i = blank(source, i, Math.min(n, j + 1), out);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int blank(String source, int from, int to, StringBuilder out) {
        for (int k = from; k < to; k++) {
            out.append(source.charAt(k) == '\n' ? '\n' : ' ');
        }
        return to;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path repositoryRoot() {
        String root = System.getProperty("uxm.repositoryRoot");
        assertThat(root)
                .as("the build passes uxm.repositoryRoot to the test JVM")
                .isNotNull();
        return Path.of(root).toAbsolutePath().normalize();
    }
}

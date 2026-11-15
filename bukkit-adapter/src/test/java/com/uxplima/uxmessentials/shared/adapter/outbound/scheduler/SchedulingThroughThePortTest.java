package com.uxplima.uxmessentials.shared.adapter.outbound.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** One way of running work around the port. */
    private enum Rule {
        GET_SCHEDULER("\\bgetScheduler\\b"),
        GET_ASYNC_SCHEDULER("\\bgetAsyncScheduler\\b"),
        GET_GLOBAL_REGION_SCHEDULER("\\bgetGlobalRegionScheduler\\b"),
        GET_REGION_SCHEDULER("\\bgetRegionScheduler\\b"),
        BUKKIT_RUNNABLE("\\bBukkitRunnable\\b"),
        BUKKIT_SCHEDULER("\\bBukkitScheduler\\b"),
        NEW_THREAD("\\bnew\\s+(?:java\\.lang\\.)?Thread\\s*\\("),
        THREAD_BUILDER("\\bThread\\s*\\.\\s*of(?:Virtual|Platform)\\s*\\("),
        VIRTUAL_THREAD_START("\\bThread\\s*\\.\\s*startVirtualThread\\s*\\("),
        EXECUTORS("\\bExecutors\\s*\\.\\s*new\\w*\\s*\\("),
        THREAD_POOL_EXECUTOR("\\bnew\\s+(?:java\\.util\\.concurrent\\.)?(?:Scheduled)?ThreadPoolExecutor\\s*\\("),
        FORK_JOIN_POOL("\\bnew\\s+(?:java\\.util\\.concurrent\\.)?ForkJoinPool\\s*\\("
                + "|\\bForkJoinPool\\s*\\.\\s*commonPool\\s*\\("),
        TIMER("\\bnew\\s+(?:java\\.util\\.)?Timer\\s*\\("),
        /** Only the one-argument form, which runs on the common pool; with an executor it is the caller's choice. */
        DEFAULT_POOL_FUTURE("\\b(?:supplyAsync|runAsync)\\s*\\(");

        private final Pattern pattern;

        Rule(String regex) {
            this.pattern = Pattern.compile(regex);
        }
    }

    /** What one file may use, and why it owns that work itself. */
    private record Allowance(Set<Rule> rules, String reason) {}

    private static final String FOLIA_SCHEDULER =
            "bukkit-adapter/src/main/java/com/uxplima/uxmessentials/shared/adapter/outbound/scheduler/"
                    + "FoliaScheduler.java";

    private static final Map<String, Allowance> ALLOWED = Map.of(
            FOLIA_SCHEDULER,
            new Allowance(
                    EnumSet.of(
                            Rule.GET_SCHEDULER,
                            Rule.GET_ASYNC_SCHEDULER,
                            Rule.GET_GLOBAL_REGION_SCHEDULER,
                            Rule.GET_REGION_SCHEDULER),
                    "implements the port on Paper's schedulers"),
            "rest-adapter/src/main/java/com/uxplima/uxmessentials/rest/http/RestServer.java",
            new Allowance(
                    EnumSet.of(Rule.EXECUTORS),
                    "the HTTP server's connection threads, which never touch the Bukkit API"),
            "rest-adapter/src/main/java/com/uxplima/uxmessentials/rest/bridge/EventBridge.java",
            new Allowance(
                    EnumSet.of(Rule.THREAD_BUILDER),
                    "the HTTP event stream's sender thread, which never touches the Bukkit API"),
            "migration/src/main/java/com/uxplima/uxmessentials/migration/ImportExecutor.java",
            new Allowance(
                    EnumSet.of(Rule.THREAD_POOL_EXECUTOR),
                    "the bounded database writer pool that caps import write pressure"));

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
                        Allowance allowance = ALLOWED.get(relative);
                        Set<Rule> allowed = allowance == null ? EnumSet.noneOf(Rule.class) : allowance.rules();
                        found.addAll(directCalls(relative, read(file), allowed));
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
    void everyAllowedFileStillExists() {
        Path root = repositoryRoot();

        assertThat(ALLOWED.keySet())
                .as("an allowance for a file that moved or went away would silently cover its new home")
                .allSatisfy(file -> assertThat(root.resolve(file)).isRegularFile());
    }

    @Test
    void theAllowedAdapterStillReachesPapersSchedulers() {
        assertThat(directCalls("adapter", read(repositoryRoot().resolve(FOLIA_SCHEDULER)), EnumSet.noneOf(Rule.class)))
                .as("the pattern still matches the calls the adapter makes, so an empty scan means something")
                .isNotEmpty();
    }

    @Test
    void anAllowanceCoversOnlyItsOwnRules() {
        String code = """
                class RestServer {
                    ExecutorService connections = Executors.newVirtualThreadPerTaskExecutor();
                    void stray(Player player) {
                        player.getScheduler().run(plugin, task -> {}, null);
                    }
                }
                """;

        assertThat(directCalls("rest", code, EnumSet.of(Rule.EXECUTORS))).containsExactly("rest:4 getScheduler");
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
                        Thread.startVirtualThread(this::work);
                        new java.lang.Thread(this::work);
                        var forks = new ForkJoinPool(2);
                        ForkJoinPool.commonPool().execute(this::work);
                        CompletableFuture.supplyAsync(() -> load(a, b));
                        CompletableFuture.runAsync(this::work);
                        CompletableFuture.supplyAsync(() -> load(a, b), offTick);
                    }
                }
                """;

        assertThat(directCalls("sample", code, EnumSet.noneOf(Rule.class)))
                .containsExactly(
                        "sample:1 getAsyncScheduler",
                        "sample:4 getAsyncScheduler",
                        "sample:5 getScheduler",
                        "sample:6 new Thread(",
                        "sample:7 Executors.newFixedThreadPool(",
                        "sample:8 new Timer(",
                        "sample:9 Thread.ofVirtual(",
                        "sample:10 Thread.startVirtualThread(",
                        "sample:11 new java.lang.Thread(",
                        "sample:12 new ForkJoinPool(",
                        "sample:13 ForkJoinPool.commonPool(",
                        "sample:14 supplyAsync(",
                        "sample:15 runAsync(");
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

        assertThat(directCalls("sample", code, EnumSet.noneOf(Rule.class))).isEmpty();
    }

    private static List<String> directCalls(String name, String source, Set<Rule> allowed) {
        String code = codeOnly(source);
        List<Hit> hits = new ArrayList<>();
        for (Rule rule : Rule.values()) {
            if (allowed.contains(rule)) {
                continue;
            }
            Matcher matcher = rule.pattern.matcher(code);
            while (matcher.find()) {
                if (rule == Rule.DEFAULT_POOL_FUTURE && hasSecondArgument(code, matcher.end())) {
                    continue;
                }
                long line = code.substring(0, matcher.start())
                                .chars()
                                .filter(c -> c == '\n')
                                .count()
                        + 1;
                hits.add(new Hit(matcher.start(), name + ":" + line + " " + matcher.group()));
            }
        }
        hits.sort(Comparator.comparingInt(Hit::at));
        return hits.stream().map(Hit::text).toList();
    }

    private record Hit(int at, String text) {}

    /** Whether the call whose argument list opens just before {@code from} passes more than one argument. */
    private static boolean hasSecondArgument(String code, int from) {
        int depth = 0;
        for (int i = from; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                return true;
            }
        }
        return false;
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

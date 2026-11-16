package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.event.Cancellable;

import org.junit.jupiter.api.Test;

/**
 * The two event-listener rules the server never enforces, read from the main sources.
 *
 * <p>A handler of a cancellable event skips cancelled ones ({@code ignoreCancelled = true}) unless a comment right
 * above it says why it has to see them. Without the flag, a refused action still runs everything the handler does:
 * a death page for a death that never happened, a rule set dropped for a world that stayed loaded.
 *
 * <p>A {@code MONITOR} handler only observes. Changing the event there overrides every other plugin's decision after
 * the fact, so a handler that writes to its event belongs at {@code HIGHEST} or lower.
 */
class ListenerFlagsTest {

    private static final Pattern HANDLER = Pattern.compile("@EventHandler\\b(?:\\s*\\(([^)]*)\\))?");

    private static final Pattern PARAMETER =
            Pattern.compile("\\(\\s*(?:final\\s+)?([\\w.]+)(?:<[^>]*>)?\\s+(\\w+)\\s*\\)\\s*(?:throws[\\w.,\\s]+)?\\{");

    private static final Pattern IMPORT = Pattern.compile("^import\\s+([\\w.]+)\\.(\\w+);", Pattern.MULTILINE);

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);

    /** A write to the event: a setter, the Adventure message setters Paper adds, or the drop list. */
    private static final String EVENT_WRITE = "\\bevent\\s*\\.\\s*(?:(?:set[A-Z]\\w*|message|deathMessage|joinMessage"
            + "|quitMessage|renderer|allow|disallow)\\s*\\(|(?:getDrops|viewers)\\s*\\(\\s*\\)\\s*\\.\\s*(?:clear|add|remove))";

    /** One handler as the source declares it. */
    private record Handler(
            String where, String priority, boolean ignoresCancelled, String eventType, String comment, String body) {}

    @Test
    void everyHandlerOfACancellableEventSkipsCancelledOnesOrSaysWhy() throws IOException {
        List<Handler> handlers = mainHandlers();
        List<String> silent = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (Handler handler : handlers) {
            Optional<Class<?>> type = load(handler.eventType());
            if (type.isEmpty()) {
                unresolved.add(handler.where() + " " + handler.eventType());
                continue;
            }
            if (Cancellable.class.isAssignableFrom(type.get())
                    && !handler.ignoresCancelled()
                    && !handler.comment().toLowerCase(Locale.ROOT).contains("cancel")) {
                silent.add(handler.where() + " " + type.get().getSimpleName());
            }
        }

        assertThat(handlers).as("the scan found the listeners").hasSizeGreaterThan(300);
        assertThat(unresolved)
                .as("event types the test class path cannot load; only optional plugins' events may be missing")
                .allSatisfy(entry -> assertThat(entry).contains(" me.clip.placeholderapi."));
        assertThat(silent)
                .as("handlers of cancellable events with no ignoreCancelled = true and no comment saying why they "
                        + "must see cancelled events")
                .isEmpty();
    }

    @Test
    void noMonitorHandlerWritesToItsEvent() throws IOException {
        List<String> writers = new ArrayList<>();
        for (Handler handler : mainHandlers()) {
            if (handler.priority().equals("MONITOR") && writesToEvent(handler)) {
                writers.add(handler.where());
            }
        }

        assertThat(writers)
                .as("MONITOR handlers that change their event; move them to HIGHEST or lower")
                .isEmpty();
    }

    @Test
    void theParserReadsFlagsTypesCommentsAndBodies() {
        String source = """
                package sample;

                import org.bukkit.event.player.PlayerInteractEvent;

                class Sample {
                    // Sees cancelled clicks too: a click in the air arrives cancelled.
                    @EventHandler(priority = EventPriority.LOWEST)
                    public void onClick(PlayerInteractEvent event) {
                        event.setCancelled(true);
                    }

                    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                    public void onLocal(final LocalEvent seen) {
                        seen.getPlayer().getInventory().setItem(0, null);
                    }
                }
                """;

        List<Handler> handlers = handlers("sample", source);

        assertThat(handlers).hasSize(2);
        assertThat(handlers.get(0).where()).isEqualTo("sample:7");
        assertThat(handlers.get(0).priority()).isEqualTo("LOWEST");
        assertThat(handlers.get(0).ignoresCancelled()).isFalse();
        assertThat(handlers.get(0).eventType()).isEqualTo("org.bukkit.event.player.PlayerInteractEvent");
        assertThat(handlers.get(0).comment()).contains("Sees cancelled clicks");
        assertThat(writesToEvent(handlers.get(0))).isTrue();
        assertThat(handlers.get(1).priority()).isEqualTo("MONITOR");
        assertThat(handlers.get(1).ignoresCancelled()).isTrue();
        assertThat(handlers.get(1).eventType()).isEqualTo("sample.LocalEvent");
        assertThat(handlers.get(1).comment()).isEmpty();
        assertThat(writesToEvent(handlers.get(1))).isFalse();
    }

    private static boolean writesToEvent(Handler handler) {
        return Pattern.compile(handler.body().isEmpty() ? "(?!)" : EVENT_WRITE)
                .matcher(handler.body())
                .find();
    }

    private static List<Handler> mainHandlers() throws IOException {
        Path root = repositoryRoot();
        List<Handler> handlers = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path sources = module.resolve("src/main/java");
                if (!Files.isDirectory(sources)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(sources)) {
                    for (Path file :
                            files.filter(f -> f.toString().endsWith(".java")).toList()) {
                        String source = read(file);
                        if (source.contains("@EventHandler")) {
                            String name = root.relativize(file).toString().replace('\\', '/');
                            handlers.addAll(handlers(name, source));
                        }
                    }
                }
            }
        }
        return handlers;
    }

    private static List<Handler> handlers(String name, String source) {
        String code = codeOnly(source);
        Map<String, String> imports = new HashMap<>();
        Matcher imported = IMPORT.matcher(code);
        while (imported.find()) {
            imports.put(imported.group(2), imported.group(1) + "." + imported.group(2));
        }
        Matcher declaredPackage = PACKAGE.matcher(code);
        String pkg = declaredPackage.find() ? declaredPackage.group(1) : "";

        List<Handler> found = new ArrayList<>();
        Matcher annotation = HANDLER.matcher(code);
        while (annotation.find()) {
            String args = annotation.group(1) == null ? "" : annotation.group(1);
            Matcher parameter = PARAMETER.matcher(code);
            if (!parameter.find(annotation.end())) {
                continue;
            }
            String simple = parameter.group(1);
            String type = simple.contains(".") ? simple : imports.getOrDefault(simple, pkg + "." + simple);
            Matcher priority = Pattern.compile("EventPriority\\.(\\w+)").matcher(args);
            int bodyStart = parameter.end() - 1;
            String body = code.substring(bodyStart, blockEnd(code, bodyStart));
            found.add(new Handler(
                    name + ":" + lineOf(code, annotation.start()),
                    priority.find() ? priority.group(1) : "NORMAL",
                    args.matches("(?s).*\\bignoreCancelled\\s*=\\s*true\\b.*"),
                    type,
                    commentAbove(source, annotation.start()),
                    body.replaceAll("\\b" + Pattern.quote(parameter.group(2)) + "\\b", "event")));
        }
        return found;
    }

    /** The comment lines, and any other annotations, directly above the line at {@code offset}. */
    private static String commentAbove(String source, int offset) {
        String[] lines = source.substring(0, offset).split("\n", -1);
        StringBuilder comment = new StringBuilder();
        for (int i = lines.length - 2; i >= 0; i--) {
            String line = lines[i].strip();
            if (!(line.startsWith("//") || line.startsWith("*") || line.startsWith("/*") || line.startsWith("@"))) {
                break;
            }
            comment.insert(0, line + "\n");
        }
        return comment.toString();
    }

    private static int blockEnd(String code, int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i + 1;
            }
        }
        return code.length();
    }

    private static long lineOf(String code, int offset) {
        return code.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    }

    private static Optional<Class<?>> load(String name) {
        try {
            return Optional.of(Class.forName(name, false, ListenerFlagsTest.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError missing) {
            return Optional.empty();
        }
    }

    /**
     * The source with every comment and every string, text block and char literal blanked out, newlines kept so the
     * line numbers stay right, so a quoted brace or an annotation named in a comment is not read as code.
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
                i = blank(source, i, end < 0 ? n : end + 2, out);
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                while (end > 0 && source.charAt(end - 1) == '\\') {
                    end = source.indexOf("\"\"\"", end + 1);
                }
                i = blank(source, i, end < 0 ? n : end + 3, out);
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
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
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

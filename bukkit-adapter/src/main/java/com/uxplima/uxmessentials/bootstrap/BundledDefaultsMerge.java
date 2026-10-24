package com.uxplima.uxmessentials.bootstrap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Works out which settings an update added to a bundled default file, so they can be appended to the operator's
 * own copy instead of existing only inside the jar.
 *
 * <p>It is a three-way comparison. The <em>bundled</em> text is what this version ships, the <em>baseline</em> is
 * the bundled text the operator's file was last reconciled against (kept under {@code .defaults/}), and the
 * <em>operator</em> text is the file they actually edit. A key is new only when the bundled default has it and
 * neither the baseline nor the operator's file does. That distinction is the whole point: without the baseline
 * there is no way to tell a setting an update introduced from a setting the operator deliberately deleted, and
 * re-adding the second kind on every restart would quietly undo their work.
 *
 * <p>The result is rendered as a HOCON fragment holding only the new keys, with the comments that explain them.
 * Appending it is safe because HOCON merges repeated objects: a fragment that names only absent keys can never
 * change a value the operator set. The one shape that would break that rule is a key they turned into a
 * different type than we now ship (a scalar where the default is a block), so those are skipped and their file
 * is left exactly as it is.
 */
@NullMarked
final class BundledDefaultsMerge {

    /**
     * Settings this version ships under a new name, each with the name it had before. A file that still writes the
     * old name has the setting, so the new name is not appended to it: the storage keys are read new name first,
     * and an appended {@code type = "sqlite"} would win over the operator's {@code backend = "mysql"}.
     */
    private static final Map<List<Object>, String> FORMER_NAMES = Map.of(
            List.of("storage", "type"), "backend",
            List.of("storage", "pool-size"), "read-pool-size");

    private BundledDefaultsMerge() {}

    /**
     * The HOCON fragment holding every setting {@code bundled} has that neither {@code baseline} nor
     * {@code operator} does, or empty when this update added nothing the operator is missing.
     *
     * @throws ConfigurateException if any of the three texts is not parseable, which for the operator's file
     *     means a syntax error of their own: the caller reports it and leaves the file untouched
     */
    static Optional<String> newSettings(String bundled, String baseline, String operator) throws ConfigurateException {
        CommentedConfigurationNode fragment = CommentedConfigurationNode.root();
        collect(parse(bundled), parse(baseline), parse(operator), fragment, List.of());
        return fragment.empty() ? Optional.empty() : Optional.of(render(fragment));
    }

    /** A top-level {@code "key" = value} line, the shape every line of a message catalog has. */
    private static final Pattern TOP_LEVEL_ENTRY = Pattern.compile("^\"([^\"]+)\"\\s*=.*$");

    /**
     * {@code operator} with every top-level line the operator never touched brought to this version's wording.
     *
     * <p>A line is untouched when it reads exactly as the previous version shipped it. The append in
     * {@link #newSettings} only ever adds keys, so before this a corrected message reached new servers only and
     * every existing one kept the old wording forever. A line the operator rewrote, a key that appears twice, and
     * an indented line inside a block are all left as they are.
     */
    static String updatedLines(String bundled, String baseline, String operator) {
        Map<String, String> was = topLevelLines(baseline);
        Map<String, String> now = topLevelLines(bundled);
        List<String> lines = new ArrayList<>(Arrays.asList(operator.split("\n", -1)));
        for (Map.Entry<String, String> entry : now.entrySet()) {
            String old = was.get(entry.getKey());
            if (old == null || old.equals(entry.getValue())) {
                continue;
            }
            int at = lines.indexOf(old);
            if (at >= 0 && lines.lastIndexOf(old) == at) {
                lines.set(at, entry.getValue());
            }
        }
        return String.join("\n", lines);
    }

    /** Each top-level key with its whole line. A key written twice is ambiguous, so it is left out. */
    private static Map<String, String> topLevelLines(String text) {
        Map<String, String> lines = new HashMap<>();
        Set<String> twice = new HashSet<>();
        for (String line : text.split("\n", -1)) {
            Matcher entry = TOP_LEVEL_ENTRY.matcher(line);
            if (entry.matches() && lines.put(entry.group(1), line) != null) {
                twice.add(entry.group(1));
            }
        }
        lines.keySet().removeAll(twice);
        return lines;
    }

    /** Walks the bundled tree, copying into {@code fragment} every branch that is new in this version. */
    private static void collect(
            CommentedConfigurationNode bundled,
            CommentedConfigurationNode baseline,
            CommentedConfigurationNode operator,
            CommentedConfigurationNode fragment,
            List<Object> path) {
        for (Map.Entry<Object, CommentedConfigurationNode> entry :
                bundled.childrenMap().entrySet()) {
            Object key = entry.getKey();
            CommentedConfigurationNode child = entry.getValue();
            CommentedConfigurationNode theirs = operator.node(key);
            List<Object> at = Stream.concat(path.stream(), Stream.of(key)).toList();
            if (!theirs.virtual()) {
                // They have it. Only a block can still be missing something inside it, and only if they kept it
                // a block; a scalar where we ship an object is left alone rather than replaced.
                if (child.isMap() && theirs.isMap()) {
                    collect(child, baseline.node(key), theirs, fragment.node(key), at);
                }
            } else if (baseline.node(key).virtual() && !writtenUnderFormerName(operator, at)) {
                copy(child, fragment.node(key));
            }
        }
    }

    /** Whether the operator's block holds the setting at {@code path} under the name it had before. */
    private static boolean writtenUnderFormerName(CommentedConfigurationNode operator, List<Object> path) {
        String former = FORMER_NAMES.get(path);
        return former != null && !operator.node(former).virtual();
    }

    /** Deep-copies {@code from} into {@code to}, comments included, so an appended block reads like the file. */
    private static void copy(CommentedConfigurationNode from, CommentedConfigurationNode to) {
        to.comment(from.comment());
        if (!from.isMap()) {
            to.raw(from.raw());
            return;
        }
        for (Map.Entry<Object, CommentedConfigurationNode> entry :
                from.childrenMap().entrySet()) {
            copy(entry.getValue(), to.node(entry.getKey()));
        }
        if (to.empty()) {
            // An empty block in the defaults (an override map the operator fills in) still has to arrive.
            to.raw(Map.of());
        }
    }

    private static CommentedConfigurationNode parse(String text) throws ConfigurateException {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(text)))
                .build()
                .load();
    }

    private static String render(CommentedConfigurationNode fragment) throws ConfigurateException {
        StringWriter text = new StringWriter();
        HoconConfigurationLoader.builder()
                .sink(() -> new BufferedWriter(text))
                .build()
                .save(fragment);
        return text.toString();
    }
}

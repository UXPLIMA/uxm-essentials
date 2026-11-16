package com.uxplima.uxmessentials.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Files outside the core module that a core test reads: the repository root the build passes in, and the English
 * description line of every command, which the adapter's messages file holds and the command reference quotes.
 */
public final class RepositoryFiles {

    /** The messages file whose {@code describe.<id>} lines are what /help shows an English reader. */
    public static final String ENGLISH_MESSAGES = "bukkit-adapter/src/main/resources/messages/messages_en.conf";

    private static final Pattern DESCRIBE =
            Pattern.compile("^\"describe\\.([^\"]+)\"\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*$");

    private RepositoryFiles() {}

    /** The repository root, which the build passes to the test JVM as {@code uxm.repositoryRoot}. */
    public static Path root() {
        String root = System.getProperty("uxm.repositoryRoot");
        if (root == null) {
            throw new IllegalStateException("the build passes uxm.repositoryRoot to the test JVM");
        }
        return Path.of(root).toAbsolutePath().normalize();
    }

    /** Every {@code describe.<id>} line of the English messages, keyed by command id, in file order. */
    public static Map<String, String> commandDescriptions() {
        Map<String, String> lines = new LinkedHashMap<>();
        for (String line : read(root().resolve(ENGLISH_MESSAGES)).lines().toList()) {
            Matcher match = DESCRIBE.matcher(line);
            if (match.matches()) {
                lines.put(match.group(1), unescape(match.group(2)));
            }
        }
        return Map.copyOf(lines);
    }

    private static String unescape(String quoted) {
        return quoted.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}

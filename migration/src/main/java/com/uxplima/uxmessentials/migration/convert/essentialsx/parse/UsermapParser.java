package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * Reads EssentialsX's {@code usermap.csv}: one {@code name,uuid} line for every account that ever joined, including
 * the ones whose userdata file is gone. The last line for an account wins, as EssentialsX appends a line when a
 * player joins under a new name. A line that is not a name and a uuid is skipped.
 */
@NullMarked
public final class UsermapParser {

    /** The accounts in {@code file} by uuid, each under the last name written for it; empty when there is none. */
    public Map<UUID, String> parse(Path file) throws IOException {
        Map<UUID, String> names = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return names;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comma = line.lastIndexOf(',');
                if (comma <= 0) {
                    continue;
                }
                String name = line.substring(0, comma).strip();
                try {
                    UUID uuid = UUID.fromString(line.substring(comma + 1).strip());
                    names.remove(uuid);
                    names.put(uuid, name);
                } catch (IllegalArgumentException notAUuid) {
                    // A header, a comment or a damaged line names no account.
                }
            }
        }
        return names;
    }
}

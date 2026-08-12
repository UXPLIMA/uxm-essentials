package com.uxplima.uxmessentials.docs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Reads a shipped module config into the rows the Settings table on a module page is built from. The shipped
 * files already carry one explanatory comment per key, which is where the description column comes from:
 * writing it a second time in the page is what let the two drift apart before.
 */
final class ShippedConfigReader {

    private ShippedConfigReader() {}

    static List<DocsData.Setting> read(String moduleId) {
        String resource = "/modules/" + moduleId + "/config.conf";
        try (InputStream in = ShippedConfigReader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("no shipped config at " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return parse(reader.lines().toList());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + resource, e);
        }
    }

    static List<DocsData.Setting> parse(List<String> lines) {
        List<DocsData.Setting> settings = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();
        int listDepth = 0;
        for (String raw : lines) {
            String line = raw.strip();
            if (listDepth > 0) {
                listDepth += count(line, '[') - count(line, ']');
                continue;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equals("}")) {
                path.pollLast();
                continue;
            }
            if (line.endsWith("{")) {
                path.addLast(line.substring(0, line.length() - 1).strip());
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = line.substring(0, equals).strip();
            String rest = line.substring(equals + 1);
            int hash = rest.indexOf('#');
            String value = (hash < 0 ? rest : rest.substring(0, hash)).strip();
            String description = hash < 0 ? "" : rest.substring(hash + 1).strip();
            if (value.equals("[")) {
                listDepth = 1;
                value = "[...]";
            }
            settings.add(new DocsData.Setting(qualify(path, key), value, description));
        }
        return List.copyOf(settings);
    }

    private static String qualify(Deque<String> path, String key) {
        return path.isEmpty() ? key : String.join(".", path) + "." + key;
    }

    private static int count(String line, char c) {
        int total = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == c) {
                total++;
            }
        }
        return total;
    }
}

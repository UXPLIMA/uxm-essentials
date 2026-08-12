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
        List<String> comment = new ArrayList<>();
        int listDepth = 0;
        for (String raw : lines) {
            String line = raw.strip();
            if (listDepth > 0) {
                listDepth += count(line, '[') - count(line, ']');
                continue;
            }
            if (line.isEmpty()) {
                comment.clear();
                continue;
            }
            if (line.startsWith("#")) {
                comment.add(line.substring(1).strip());
                continue;
            }
            if (line.equals("}")) {
                path.pollLast();
                comment.clear();
                continue;
            }
            if (line.endsWith("{")) {
                path.addLast(line.substring(0, line.length() - 1).strip());
                comment.clear();
                continue;
            }
            if (line.endsWith("}") && line.contains("{")) {
                int brace = line.indexOf('{');
                path.addLast(line.substring(0, brace).strip());
                for (String member : commaSeparated(line.substring(brace + 1, line.lastIndexOf('}')))) {
                    int assign = member.indexOf('=');
                    if (assign > 0) {
                        settings.add(new DocsData.Setting(
                                qualify(path, member.substring(0, assign).strip()),
                                member.substring(assign + 1).strip(),
                                String.join(" ", comment)));
                    }
                }
                path.pollLast();
                comment.clear();
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                comment.clear();
                continue;
            }
            String key = line.substring(0, equals).strip();
            String rest = line.substring(equals + 1);
            int hash = rest.indexOf('#');
            String value = (hash < 0 ? rest : rest.substring(0, hash)).strip();
            String description = hash < 0
                    ? String.join(" ", comment)
                    : rest.substring(hash + 1).strip();
            if (value.equals("[")) {
                listDepth = 1;
                value = "[...]";
            }
            settings.add(new DocsData.Setting(qualify(path, key), value, description));
            comment.clear();
        }
        return List.copyOf(settings);
    }

    /** Splits the members of a one-line object ({@code void { biome = "plains" }}) on their commas. */
    private static List<String> commaSeparated(String inside) {
        List<String> members = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= inside.length(); index++) {
            if (index == inside.length() || inside.charAt(index) == ',') {
                String member = inside.substring(start, index).strip();
                if (!member.isEmpty()) {
                    members.add(member);
                }
                start = index + 1;
            }
        }
        return List.copyOf(members);
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

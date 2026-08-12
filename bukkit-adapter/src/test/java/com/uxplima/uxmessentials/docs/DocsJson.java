package com.uxplima.uxmessentials.docs;

import java.util.List;
import java.util.function.Consumer;

/** Renders the documentation model as JSON. Hand written so the export needs no new dependency. */
final class DocsJson {

    private DocsJson() {}

    static String render(List<DocsData.Module> modules) {
        StringBuilder out = new StringBuilder("{\n  \"modules\": [\n");
        for (int i = 0; i < modules.size(); i++) {
            DocsData.Module module = modules.get(i);
            out.append("    {\n");
            out.append("      \"id\": ").append(quote(module.id())).append(",\n");
            out.append("      \"configPath\": ")
                    .append(quote(module.configPath()))
                    .append(",\n");
            out.append("      \"enabledByDefault\": ")
                    .append(module.enabledByDefault())
                    .append(",\n");
            array(out, "commands", module.commands(), command -> {
                out.append("        {\"literal\": ").append(quote(command.literal()));
                out.append(", \"aliases\": [");
                for (int a = 0; a < command.aliases().size(); a++) {
                    out.append(a == 0 ? "" : ", ")
                            .append(quote(command.aliases().get(a)));
                }
                out.append("], \"permission\": ").append(quote(command.permission()));
                out.append(", \"description\": ")
                        .append(quote(command.description()))
                        .append("}");
            });
            array(out, "permissions", module.permissions(), permission -> {
                out.append("        {\"node\": ").append(quote(permission.node()));
                out.append(", \"fallback\": ").append(quote(permission.fallback()));
                out.append(", \"shape\": ").append(quote(permission.shape()));
                out.append(", \"description\": ")
                        .append(quote(permission.description()))
                        .append("}");
            });
            array(out, "settings", module.settings(), setting -> {
                out.append("        {\"key\": ").append(quote(setting.key()));
                out.append(", \"value\": ").append(quote(setting.value()));
                out.append(", \"description\": ")
                        .append(quote(setting.description()))
                        .append("}");
            });
            array(out, "placeholders", module.placeholders(), placeholder -> {
                out.append("        {\"key\": ").append(quote(placeholder.key()));
                out.append(", \"scope\": ").append(quote(placeholder.scope()));
                out.append(", \"description\": ")
                        .append(quote(placeholder.description()))
                        .append("}");
            });
            out.setLength(out.length() - 2);
            out.append("\n    }").append(i == modules.size() - 1 ? "\n" : ",\n");
        }
        return out.append("  ]\n}\n").toString();
    }

    private static <T> void array(StringBuilder out, String name, List<T> rows, Consumer<T> row) {
        out.append("      \"").append(name).append("\": [");
        if (rows.isEmpty()) {
            out.append("],\n");
            return;
        }
        out.append("\n");
        for (int i = 0; i < rows.size(); i++) {
            row.accept(rows.get(i));
            out.append(i == rows.size() - 1 ? "\n" : ",\n");
        }
        out.append("      ],\n");
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}

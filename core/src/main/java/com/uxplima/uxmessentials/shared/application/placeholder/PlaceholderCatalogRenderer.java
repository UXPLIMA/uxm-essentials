package com.uxplima.uxmessentials.shared.application.placeholder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Turns the placeholder catalogue into text: the pages {@code /uxmess placeholders} prints, and its export. */
public final class PlaceholderCatalogRenderer {

    /** How many keys one page of the in-game listing carries. */
    public static final int PAGE_SIZE = 8;

    private PlaceholderCatalogRenderer() {}

    /** One page of an area's keys, plus which page of how many this is. */
    public record Page(String area, int number, int of, List<String> lines) {

        public Page {
            Objects.requireNonNull(area, "area");
            lines = List.copyOf(lines);
        }

        /** Whether the area had nothing to show, which is how an unknown area reads. */
        public boolean empty() {
            return lines.isEmpty();
        }
    }

    /** The opening screen: every area with how many keys it owns. */
    public static List<String> areas() {
        List<String> lines = new ArrayList<>();
        for (String area : PlaceholderCatalog.areas()) {
            int count = PlaceholderCatalog.forArea(area).size();
            lines.add(area + ": " + count + (count == 1 ? " key" : " keys"));
        }
        return List.copyOf(lines);
    }

    /** One page of {@code area}, clamped into range so a page past the end shows the last page rather than nothing. */
    public static Page page(String area, int requested) {
        Objects.requireNonNull(area, "area");
        List<PlaceholderSpec> entries = PlaceholderCatalog.forArea(area.toLowerCase(Locale.ROOT));
        if (entries.isEmpty()) {
            return new Page(area, 1, 1, List.of());
        }
        int pages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int number = Math.clamp(requested, 1, pages);
        int from = (number - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        List<String> lines = new ArrayList<>();
        for (PlaceholderSpec spec : entries.subList(from, to)) {
            lines.add(line(spec));
        }
        return new Page(area, number, pages, lines);
    }

    /** One key as a single line: what an operator types, and what it renders. */
    public static String line(PlaceholderSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return spec.placeholder() + " [" + spec.scope().name().toLowerCase(Locale.ROOT) + "] " + spec.description();
    }

    /** The whole catalogue as markdown, area by area, for an operator who wants it in a file. */
    public static String markdown() {
        StringBuilder out = new StringBuilder("# uxmEssentials placeholders\n");
        for (String area : PlaceholderCatalog.areas()) {
            out.append("\n## ").append(area).append("\n\n");
            out.append("| Placeholder | Reads | What it renders |\n");
            out.append("|-------------|-------|-----------------|\n");
            for (PlaceholderSpec spec : PlaceholderCatalog.forArea(area)) {
                out.append("| `")
                        .append(spec.placeholder())
                        .append("` | ")
                        .append(spec.scope().name().toLowerCase(Locale.ROOT))
                        .append(" | ")
                        .append(spec.description().replace("|", "\\|"))
                        .append(" |\n");
            }
        }
        return out.toString();
    }

    /** The area an operator most likely meant, when what they typed is not one. */
    public static Optional<String> suggest(String typed) {
        Objects.requireNonNull(typed, "typed");
        String wanted = typed.toLowerCase(Locale.ROOT);
        return PlaceholderCatalog.areas().stream()
                .filter(area -> area.startsWith(wanted) || wanted.startsWith(area))
                .findFirst();
    }
}

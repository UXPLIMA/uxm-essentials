package com.uxplima.uxmessentials.shared.application.permission;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns the catalogue into text: the pages {@code /uxmess permissions} prints, and the markdown an operator exports.
 *
 * <p>Pure application code with no rendering dependency, so the same lines can be asserted in a test and delivered
 * through whatever the adapter uses to talk to a sender.
 */
public final class PermissionCatalogRenderer {

    /** How many nodes one page of the in-game listing carries. */
    public static final int PAGE_SIZE = 8;

    private PermissionCatalogRenderer() {}

    /** One page of an area's nodes, plus the header that says which page of how many this is. */
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

    /** The opening screen: every area with how many nodes it owns. */
    public static List<String> areas() {
        List<String> lines = new ArrayList<>();
        for (String area : PermissionCatalog.areas()) {
            int count = PermissionCatalog.forArea(area).size();
            lines.add(area + ": " + count + (count == 1 ? " node" : " nodes"));
        }
        return List.copyOf(lines);
    }

    /**
     * One page of {@code area}, clamped into range so a page number past the end shows the last page rather than
     * nothing. Page numbers are what an operator types, so they start at one.
     */
    public static Page page(String area, int requested) {
        Objects.requireNonNull(area, "area");
        List<PermissionSpec> entries = PermissionCatalog.forArea(area.toLowerCase(Locale.ROOT));
        if (entries.isEmpty()) {
            return new Page(area, 1, 1, List.of());
        }
        int pages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int number = Math.clamp(requested, 1, pages);
        int from = (number - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        List<String> lines = new ArrayList<>();
        for (PermissionSpec spec : entries.subList(from, to)) {
            lines.add(line(spec));
        }
        return new Page(area, number, pages, lines);
    }

    /** One node as a single line: what it is called, who holds it by default, and what it does. */
    public static String line(PermissionSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return spec.node() + " [" + fallback(spec) + "] " + spec.description();
    }

    /** The whole catalogue as markdown, area by area, for an operator who wants it in a file. */
    public static String markdown() {
        StringBuilder out = new StringBuilder("# uxmEssentials permissions\n");
        for (String area : PermissionCatalog.areas()) {
            out.append("\n## ").append(area).append("\n\n");
            out.append("| Node | Default | What it grants |\n");
            out.append("|------|---------|----------------|\n");
            for (PermissionSpec spec : PermissionCatalog.forArea(area)) {
                out.append("| `")
                        .append(spec.node())
                        .append("` | `")
                        .append(fallback(spec))
                        .append("` | ")
                        .append(spec.description().replace("|", "\\|"))
                        .append(" |\n");
            }
        }
        return out.toString();
    }

    /**
     * How the default reads. A family carries no boolean default at all: holding
     * {@code uxmessentials.home.limit.5} is a value, not a yes, so saying "op" or "true" about the shape would be a
     * lie. Those show the shape instead.
     */
    private static String fallback(PermissionSpec spec) {
        if (!spec.registrable()) {
            return spec.shape().name().toLowerCase(Locale.ROOT);
        }
        return spec.fallback().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** The area an operator most likely meant, when what they typed is not one. */
    public static Optional<String> suggest(String typed) {
        Objects.requireNonNull(typed, "typed");
        String wanted = typed.toLowerCase(Locale.ROOT);
        return PermissionCatalog.areas().stream()
                .filter(area -> area.startsWith(wanted) || wanted.startsWith(area))
                .findFirst();
    }
}

package com.uxplima.uxmessentials.shared.application.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Every command the plugin ships, in one list.
 *
 * <p>The adapter decides what a command does; this decides what is published about it. Three things read it: the
 * command catalog folds each entry's aliases into the defaults it resolves, so an alias exists because it is written
 * here; the plugin compares every registration against it on enable and warns about a command that is missing or
 * gated by another node; and the command reference under {@code docs/user/} is generated from it, with a test that
 * fails when the committed page falls behind. A command an operator defines in a file (a custom command, an info page
 * of their own, a menu's open command) is content, not code, and is not listed.
 *
 * <p>The tables are split by area so no one file carries every row. Order within an area is by module, then by id.
 */
public final class CommandSpecCatalog {

    private static final List<CommandSpec> ALL = Stream.of(
                    SharedCommandSpecs.all(),
                    TeleportCommandSpecs.all(),
                    DestinationCommandSpecs.all(),
                    EconomyCommandSpecs.all(),
                    EnforcementCommandSpecs.all(),
                    SocialCommandSpecs.all(),
                    PlayerstateCommandSpecs.all(),
                    ItemworldCommandSpecs.all(),
                    ContentCommandSpecs.all())
            .flatMap(List::stream)
            .toList();

    private CommandSpecCatalog() {}

    /** Every entry, area by area. */
    public static List<CommandSpec> all() {
        return ALL;
    }

    /** The areas that have commands, with {@code shared} first and the modules alphabetically after it. */
    public static List<String> areas() {
        List<String> areas = new ArrayList<>(
                ALL.stream().map(CommandSpec::area).distinct().sorted().toList());
        if (areas.remove("shared")) {
            areas.addFirst("shared");
        }
        return List.copyOf(areas);
    }

    /** The commands of one area, by name, or nothing when the area has none. */
    public static List<CommandSpec> forArea(String area) {
        Objects.requireNonNull(area, "area");
        return ALL.stream()
                .filter(spec -> spec.area().equals(area))
                .sorted(Comparator.comparing(CommandSpec::name))
                .toList();
    }

    /** The entry whose catalog id is {@code id}. */
    public static Optional<CommandSpec> find(String id) {
        Objects.requireNonNull(id, "id");
        return ALL.stream().filter(spec -> spec.id().equals(id)).findFirst();
    }
}

package com.uxplima.uxmessentials.shared.application.permission;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Every permission the plugin publishes, in one place.
 *
 * <p>This is the source the rest of the permission surface is derived from. The adapter registers the fixed entries
 * with the server on enable, so a permission plugin can suggest and complete them; {@code /uxmess permissions} reads
 * the same list back to an operator in game; and the published reference page is checked against it, so a node
 * cannot quietly exist without being documented. Nothing else declares a node, which is the point: the audit that
 * produced this catalogue found thirty-nine live nodes the server was never told about, two that were advertised
 * under a name no code reads, and four that nothing read at all, all because a node had to be written down in three
 * places that nothing kept in step.
 *
 * <p>The tables are split by area purely so no one file carries four hundred rows. Order within an area is
 * alphabetical by node, and {@link #all()} returns them area by area.
 */
public final class PermissionCatalog {

    private static final List<PermissionSpec> ALL = Stream.of(
                    SharedPermissions.all(),
                    TeleportPermissions.all(),
                    DestinationPermissions.all(),
                    EconomyPermissions.all(),
                    EnforcementPermissions.all(),
                    SocialPermissions.all(),
                    PlayerstatePermissions.all(),
                    ItemworldPermissions.all(),
                    ContentPermissions.all())
            .flatMap(List::stream)
            .toList();

    private PermissionCatalog() {}

    /** Every entry, area by area and alphabetical within an area. */
    public static List<PermissionSpec> all() {
        return ALL;
    }

    /** The entries the server can be told about: the fixed nodes, families excluded. */
    public static List<PermissionSpec> registrable() {
        return ALL.stream().filter(PermissionSpec::registrable).toList();
    }

    /** The areas that have entries, sorted, with {@code shared} first because it is the kernel's own. */
    public static List<String> areas() {
        return ALL.stream()
                .map(PermissionSpec::area)
                .distinct()
                .sorted(Comparator.comparing((String area) -> !area.equals("shared"))
                        .thenComparing(area -> area))
                .toList();
    }

    /** Every entry belonging to one area, which is a module id or {@code shared}. */
    public static List<PermissionSpec> forArea(String area) {
        Objects.requireNonNull(area, "area");
        return ALL.stream().filter(spec -> spec.area().equals(area)).toList();
    }

    /**
     * The entry that governs {@code node}: the fixed entry of that exact name, or the family it belongs to. When
     * both could match, the fixed entry wins, since a family is the fallback for what nobody wrote down whole.
     */
    public static Optional<PermissionSpec> find(String node) {
        Objects.requireNonNull(node, "node");
        return ALL.stream()
                .filter(PermissionSpec::registrable)
                .filter(spec -> spec.node().equals(node))
                .findFirst()
                .or(() -> ALL.stream()
                        .filter(spec -> !spec.registrable())
                        .filter(spec -> spec.covers(node))
                        .max(Comparator.comparingInt(spec -> spec.prefix().length())));
    }
}

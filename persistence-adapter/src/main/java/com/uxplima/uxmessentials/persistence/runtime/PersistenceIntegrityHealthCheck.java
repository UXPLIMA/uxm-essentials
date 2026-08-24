package com.uxplima.uxmessentials.persistence.runtime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.notExists;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.trueCondition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.health.RepairResult;
import com.uxplima.uxmessentials.shared.application.health.RepairableHealthCheck;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * Cross-context relational integrity probe for the application-enforced relationships in the portable schema.
 *
 * <p>Several child tables deliberately have no database foreign key because the same migrations must behave on
 * SQLite, MySQL/MariaDB and PostgreSQL. Repositories delete those children transactionally, but interrupted legacy
 * imports or manual database edits can still leave ownerless rows. This check finds them without loading domain
 * objects. Its repair deletes only those unambiguous child rows and clears nullable links whose target vanished.
 * Parent/location records are never deleted: a reference to a missing world may become valid after the world is
 * restored, so the doctor reports and retains it.
 */
@NullMarked
public final class PersistenceIntegrityHealthCheck implements RepairableHealthCheck {

    private static final List<Relation> RELATIONS = List.of(
            relation("hologram_lines", "holograms", key("hologram", "name")),
            relation("hologram_pages", "holograms", key("hologram", "name")),
            relation("hologram_action", "holograms", key("hologram_name", "name")),
            relation("hologram_blacklist", "holograms", key("hologram_name", "name")),
            relation("hologram_manual_viewer", "holograms", key("hologram_name", "name")),
            relation("npc_action", "npc", key("npc_name", "name")),
            relation("npc_type_data", "npc", key("npc_name", "name")),
            relation("warp_ratings", "warps", key("warp_name", "name")),
            relation("world_setting", "world", key("world_name", "name")),
            relation("home_invites", "homes", key("owner", "owner"), key("slot", "slot")),
            relation("player_warp_ratings", "player_warps", key("warp_id", "id")),
            relation("player_warp_visits", "player_warps", key("warp_id", "id")),
            relation("player_warp_bans", "player_warps", key("warp_id", "id")),
            relation("player_warp_whitelist", "player_warps", key("warp_id", "id")),
            relation("player_warp_members", "player_warps", key("warp_id", "id")),
            relation("player_warp_favourites", "player_warps", key("warp_id", "id")),
            relation("player_warp_payments", "player_warps", key("warp_id", "id")),
            relation("player_warp_rating_rewards", "player_warps", key("warp_id", "id")),
            relation("player_warp_pending_teleports", "player_warps", key("warp_id", "id")));

    private static final List<NullableLink> NULLABLE_LINKS = List.of(
            new NullableLink("holograms", "linked_npc_name", "npc", "name"),
            new NullableLink("warps", "category_id", "warp_categories", "id"),
            new NullableLink("player_warps", "category_id", "warp_categories", "id"),
            new NullableLink("warp_categories", "parent_category_id", "warp_categories", "id"));

    private static final List<WorldReference> WORLD_REFERENCES = List.of(
            new WorldReference("world", "name"),
            new WorldReference("homes", "world_name"),
            new WorldReference("warps", "world_name"),
            new WorldReference("player_warps", "world_name"),
            new WorldReference("teleport_spawns", "world_name"),
            new WorldReference("teleport_main_spawn", "world_name"),
            new WorldReference("teleport_named_spawns", "world_name"),
            new WorldReference("holograms", "world_name"),
            new WorldReference("npc", "world_name"),
            new WorldReference("moderation_jail_locations", "world_name"));

    private final DSLContext dsl;
    private final Path worldContainer;

    public PersistenceIntegrityHealthCheck(DSLContext dsl, Path worldContainer) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
        this.worldContainer = Objects.requireNonNull(worldContainer, "worldContainer")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public String name() {
        return "data-integrity";
    }

    @Override
    public HealthResult check() {
        Scan scan = scan(dsl);
        if (scan.orphanRows() == 0
                && scan.danglingLinks() == 0
                && scan.missingWorlds().isEmpty()) {
            return HealthResult.ok("orphanRows=0 danglingLinks=0 missingWorlds=0");
        }
        return HealthResult.warn("orphanRows=" + scan.orphanRows() + " danglingLinks=" + scan.danglingLinks()
                + " missingWorlds=" + renderWorlds(scan.missingWorlds())
                + "; repair removes only orphan rows and dangling nullable links");
    }

    @Override
    public RepairResult repair() {
        int changed = dsl.transactionResult(configuration -> repair(configuration.dsl()));
        Set<String> missingWorlds = missingWorlds(dsl, availableTables(dsl));
        String retained = missingWorlds.isEmpty()
                ? ""
                : "; retained location records for missingWorlds=" + renderWorlds(missingWorlds);
        return changed == 0
                ? RepairResult.unchanged("changedRows=0" + retained)
                : RepairResult.repaired("changedRows=" + changed + retained);
    }

    Scan scan(DSLContext scope) {
        Set<String> tables = availableTables(scope);
        int orphans = RELATIONS.stream()
                .filter(relation -> relation.availableIn(tables))
                .mapToInt(relation -> countOrphans(scope, relation))
                .sum();
        int dangling = NULLABLE_LINKS.stream()
                .filter(link -> link.availableIn(tables))
                .mapToInt(link -> countDangling(scope, link))
                .sum();
        return new Scan(orphans, dangling, missingWorlds(scope, tables));
    }

    private int repair(DSLContext scope) {
        Set<String> tables = availableTables(scope);
        int changed = 0;
        for (Relation relation : RELATIONS) {
            if (relation.availableIn(tables)) {
                Table<?> child = table(DSL.name(relation.childTable()));
                changed +=
                        scope.deleteFrom(child).where(orphanCondition(relation)).execute();
            }
        }
        for (NullableLink link : NULLABLE_LINKS) {
            if (link.availableIn(tables)) {
                Table<?> child = table(DSL.name(link.childTable()));
                Field<Object> childKey = objectField(link.childTable(), link.childColumn());
                changed += scope.update(child)
                        .setNull(childKey)
                        .where(danglingCondition(link))
                        .execute();
            }
        }
        return changed;
    }

    private int countOrphans(DSLContext scope, Relation relation) {
        return scope.selectCount()
                .from(table(DSL.name(relation.childTable())))
                .where(orphanCondition(relation))
                .fetchOne(0, int.class);
    }

    private static Condition orphanCondition(Relation relation) {
        Table<?> parent = table(DSL.name(relation.parentTable()));
        Condition match = trueCondition();
        for (Key key : relation.keys()) {
            match = match.and(objectField(relation.parentTable(), key.parentColumn())
                    .eq(objectField(relation.childTable(), key.childColumn())));
        }
        return notExists(selectOne().from(parent).where(match));
    }

    private int countDangling(DSLContext scope, NullableLink link) {
        return scope.selectCount()
                .from(table(DSL.name(link.childTable())))
                .where(danglingCondition(link))
                .fetchOne(0, int.class);
    }

    private static Condition danglingCondition(NullableLink link) {
        Field<Object> childKey = objectField(link.childTable(), link.childColumn());
        Table<?> parent = table(DSL.name(link.parentTable()));
        Condition targetExists =
                objectField(link.parentTable(), link.parentColumn()).eq(childKey);
        return childKey.isNotNull().and(notExists(selectOne().from(parent).where(targetExists)));
    }

    private Set<String> missingWorlds(DSLContext scope, Set<String> tables) {
        Set<String> referenced = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (WorldReference reference : WORLD_REFERENCES) {
            if (!tables.contains(reference.table())) {
                continue;
            }
            Field<String> worldName = field(DSL.name(reference.table(), reference.column()), String.class);
            scope.selectDistinct(worldName).from(table(DSL.name(reference.table()))).fetch(worldName).stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(referenced::add);
        }
        Set<String> missing = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String world : referenced) {
            Path candidate = worldContainer.resolve(world).normalize();
            if (!candidate.startsWith(worldContainer) || !Files.isDirectory(candidate)) {
                missing.add(world);
            }
        }
        return Set.copyOf(missing);
    }

    private static Set<String> availableTables(DSLContext scope) {
        Set<String> names = new HashSet<>();
        scope.meta().getTables().forEach(table -> names.add(table.getName().toLowerCase(Locale.ROOT)));
        return Set.copyOf(names);
    }

    private static Field<Object> objectField(String table, String column) {
        return field(DSL.name(table, column), Object.class);
    }

    private static String renderWorlds(Set<String> worlds) {
        if (worlds.isEmpty()) {
            return "0";
        }
        List<String> ordered = new ArrayList<>(worlds);
        ordered.sort(String.CASE_INSENSITIVE_ORDER);
        int visible = Math.min(8, ordered.size());
        String rendered = String.join(",", ordered.subList(0, visible));
        return ordered.size() == visible ? rendered : rendered + ",+" + (ordered.size() - visible);
    }

    private static Key key(String childColumn, String parentColumn) {
        return new Key(childColumn, parentColumn);
    }

    private static Relation relation(String child, String parent, Key... keys) {
        return new Relation(child, parent, List.of(keys));
    }

    record Scan(int orphanRows, int danglingLinks, Set<String> missingWorlds) {
        Scan {
            missingWorlds = Set.copyOf(missingWorlds);
        }
    }

    private record Relation(String childTable, String parentTable, List<Key> keys) {
        boolean availableIn(Set<String> tables) {
            return tables.contains(childTable) && tables.contains(parentTable);
        }
    }

    private record Key(String childColumn, String parentColumn) {}

    private record NullableLink(String childTable, String childColumn, String parentTable, String parentColumn) {
        boolean availableIn(Set<String> tables) {
            return tables.contains(childTable) && tables.contains(parentTable);
        }
    }

    private record WorldReference(String table, String column) {}
}

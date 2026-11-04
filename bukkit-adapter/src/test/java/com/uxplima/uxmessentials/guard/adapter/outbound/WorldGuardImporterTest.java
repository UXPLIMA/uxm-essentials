package com.uxplima.uxmessentials.guard.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.ActionTree;
import com.uxplima.uxmessentials.guard.domain.BehaviourKey;
import com.uxplima.uxmessentials.guard.domain.Behaviours;
import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.Verdict;
import com.uxplima.uxmessentials.guard.domain.engine.Compiler;
import com.uxplima.uxmessentials.guard.domain.engine.WorldIndex;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** A WorldGuard regions.yml, imported and then asked the questions WorldGuard would have answered. */
class WorldGuardImporterTest {

    private static final UUID OWNER = UUID.fromString("6a1f0e46-0000-4000-8000-000000000001");
    private static final UUID MEMBER = UUID.fromString("6a1f0e46-0000-4000-8000-000000000002");
    private static final UUID BY_NAME = UUID.fromString("6a1f0e46-0000-4000-8000-000000000003");
    private static final UUID STRANGER = UUID.fromString("6a1f0e46-0000-4000-8000-000000000004");
    private static final GuardScope SCOPE = GuardScope.server("lobby");
    private static final ActionTree TREE = ActionTree.builtIn();

    private static final String REGIONS = """
            regions:
              Spawn:
                type: cuboid
                min: {x: 0, y: 0, z: 0}
                max: {x: 20, y: 100, z: 20}
                priority: 10
                owners: {unique-ids: ['%s']}
                members: {unique-ids: ['%s'], players: [Notch, Ghost]}
                flags:
                  pvp: deny
                  greeting: '&aWelcome'
                  invincible: allow
                  blocked-cmds: [/home, /tpa]
                  some-plugin-flag: true
              shop:
                type: poly2d
                min-y: 0
                max-y: 50
                points:
                - {x: 100, z: 100}
                - {x: 120, z: 100}
                - {x: 110, z: 120}
                priority: 0
                parent: spawn
                flags:
                  build: allow
                  entry: deny
              __global__:
                type: global
                flags:
                  fire-spread: deny
            """.formatted(OWNER, MEMBER);

    private final List<String> report = new ArrayList<>();

    private List<Region> imported() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(REGIONS);
        List<Region> out = new ArrayList<>();
        for (WorldGuardImporter.WgRegion wg : WorldGuardImporter.parseYaml("world", yaml)) {
            out.add(WorldGuardImporter.convert(wg, "wg-1", SCOPE, this::lookup, report));
        }
        return out;
    }

    private @Nullable UUID lookup(String name) {
        return name.equals("Notch") ? BY_NAME : null;
    }

    private static WorldIndex compile(List<Region> regions) {
        return Compiler.compile(
                TREE,
                new Compiler.Input("world", -64, 319, regions, List.of(), Behaviours.empty(), false),
                Map.of(),
                null,
                null);
    }

    private static GuardSubject player(UUID id) {
        return new GuardSubject() {
            @Override
            public UUID id() {
                return id;
            }

            @Override
            public boolean has(String node) {
                return false;
            }

            @Override
            public boolean meets(String expression) {
                return false;
            }
        };
    }

    private static Verdict check(WorldIndex index, int x, int y, int z, String action, GuardSubject who) {
        return index.check(x, y, z, TREE.indexOf(ActionId.of(action)), who);
    }

    @Test
    void membersBuildAndStrangersDoNotAsWorldGuardDefaults() throws InvalidConfigurationException {
        WorldIndex index = compile(imported());

        assertThat(check(index, 5, 5, 5, "break", player(STRANGER))).isEqualTo(Verdict.DENY);
        assertThat(check(index, 5, 5, 5, "break", player(MEMBER))).isEqualTo(Verdict.ALLOW);
        assertThat(check(index, 5, 5, 5, "use.container", player(OWNER))).isEqualTo(Verdict.ALLOW);
        assertThat(check(index, 5, 5, 5, "place", player(BY_NAME))).isEqualTo(Verdict.ALLOW);
    }

    @Test
    void stateFlagsBecomeRulesForTheGroupTheyHad() throws InvalidConfigurationException {
        WorldIndex index = compile(imported());

        assertThat(check(index, 5, 5, 5, "damage.player", player(OWNER))).isEqualTo(Verdict.DENY);
        assertThat(check(index, 110, 10, 105, "move.enter", player(STRANGER))).isEqualTo(Verdict.DENY);
        assertThat(check(index, 110, 10, 105, "break", player(STRANGER))).isEqualTo(Verdict.ALLOW);
        assertThat(check(index, 500, 60, 500, "world.fire.spread", GuardSubject.WORLD))
                .isEqualTo(Verdict.DENY);
    }

    @Test
    void messagesAndCommandListsBecomeBehaviours() throws InvalidConfigurationException {
        Region spawn = imported().stream()
                .filter(r -> r.id().value().equals("spawn"))
                .findFirst()
                .orElseThrow();

        assertThat(spawn.behaviours().get(BehaviourKey.MESSAGE_ENTER)).contains("<green>Welcome");
        assertThat(spawn.behaviours().get(BehaviourKey.INVINCIBLE)).contains("true");
        assertThat(spawn.behaviours().get(BehaviourKey.COMMANDS_DENY)).contains("home,tpa");
    }

    @Test
    void namesAreLoweredAndTheParentAndPriorityKept() throws InvalidConfigurationException {
        List<Region> regions = imported();
        Region shop = regions.stream()
                .filter(r -> r.id().value().equals("shop"))
                .findFirst()
                .orElseThrow();

        assertThat(regions).extracting(r -> r.id().value()).containsExactlyInAnyOrder("spawn", "shop", "wg-global");
        assertThat(shop.parent()).isEqualTo(RegionId.of("spawn"));
        assertThat(regions).allSatisfy(r -> assertThat(r.importBatch()).isEqualTo("wg-1"));
    }

    @Test
    void whatCannotBeConvertedIsReportedNotDropped() throws InvalidConfigurationException {
        imported();

        assertThat(report).anyMatch(line -> line.contains("Ghost"));
        assertThat(report).anyMatch(line -> line.contains("some-plugin-flag"));
    }
}

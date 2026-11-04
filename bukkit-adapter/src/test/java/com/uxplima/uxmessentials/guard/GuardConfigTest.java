package com.uxplima.uxmessentials.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.guard.application.GuardConfig;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.ActionTree;
import com.uxplima.uxmessentials.guard.domain.BehaviourKey;
import com.uxplima.uxmessentials.guard.domain.Effect;
import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Target;
import com.uxplima.uxmessentials.guard.domain.Template;
import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The guard config as an operator writes it: the shipped file, and the lines an operator gets wrong. */
class GuardConfigTest {

    private static final Logger QUIET = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    @TempDir
    Path folder;

    private final List<String> problems = new ArrayList<>();

    private GuardConfig read(String hocon) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, hocon);
        return GuardConfig.from(ConfigurateConfigStore.load(file, QUIET), "lobby", problems::add);
    }

    @Test
    void theShippedFileReadsWithoutAProblem() {
        GuardConfig config = GuardConfig.from(
                ConfigurateConfigStore.bundled("modules/guard/config.conf", QUIET), "lobby", problems::add);

        assertThat(problems).isEmpty();
        assertThat(config.enabled()).isTrue();
        assertThat(config.templates()).containsOnlyKeys("spawn", "lobby", "arena", "market", "parkour", "build");
        assertThat(config.rgAlias()).isFalse();
        assertThat(config.claimDistance()).isEqualTo(16);
    }

    @Test
    void everyActionTheShippedTemplatesNameIsInTheTree() {
        GuardConfig config = GuardConfig.from(
                ConfigurateConfigStore.bundled("modules/guard/config.conf", QUIET), "lobby", problems::add);
        ActionTree tree = ActionTree.builtIn();

        for (Template template : config.templates().values()) {
            for (Rule rule : template.rules()) {
                assertThat(tree.contains(rule.action()))
                        .as("%s in %s", rule.action(), template.name())
                        .isTrue();
            }
        }
    }

    @Test
    void aLineThatIsNotARuleIsReportedAndSkipped() throws IOException {
        GuardConfig config = read("""
                templates {
                  shop {
                    rules = ["break everyone deny", "break ??? deny", "use everyone"]
                  }
                }
                """);

        assertThat(config.templates().get("shop").rules())
                .containsExactly(
                        new Rule(ActionId.of("break"), Target.parse("everyone").orElseThrow(), Effect.DENY));
        assertThat(problems).hasSize(2);
    }

    @Test
    void aConditionTargetMayHoldSpaces() throws IOException {
        GuardConfig config = read("""
                worlds {
                  world {
                    rules = ["break cond:%player_level% >= 10 allow"]
                  }
                }
                """);

        Rule rule = config.world("world").rules().get(0);
        assertThat(rule.target())
                .isEqualTo(Target.parse("cond:%player_level% >= 10").orElseThrow());
        assertThat(rule.effect()).isEqualTo(Effect.ALLOW);
    }

    @Test
    void aWorldWithoutAnEntryTakesTheDefaultOne() throws IOException {
        GuardConfig config = read("""
                worlds {
                  default { rules = ["world.fire.spread everyone deny"], high-frequency = false }
                  nether { rules = [] }
                }
                """);

        assertThat(config.world("survival").rules()).hasSize(1);
        assertThat(config.world("survival").highFrequency()).isFalse();
        assertThat(config.world("nether").rules()).isEmpty();
    }

    @Test
    void aBehaviourValueIsNormalisedAndAWrongOneReported() throws IOException {
        GuardConfig config = read("""
                templates {
                  park { behaviours = ["fly = yasak", "walk-speed = 7", "time = noon"] }
                }
                """);

        assertThat(config.templates().get("park").behaviours().get(BehaviourKey.FLY))
                .contains("deny");
        assertThat(config.templates().get("park").behaviours().get(BehaviourKey.TIME))
                .contains("6000");
        assertThat(config.templates().get("park").behaviours().get(BehaviourKey.WALK_SPEED))
                .isEmpty();
        assertThat(problems).hasSize(1);
    }

    @Test
    void aNewRegionScopeNamingASetThisServerDoesNotLoadFallsBackToTheServer() throws IOException {
        GuardConfig config = read("""
                network { shared-sets = ["hub"], new-region-scope = "set:hub" }
                """);
        GuardConfig unknown = read("""
                network { shared-sets = ["hub"], new-region-scope = "arena" }
                """);

        assertThat(config.loadedScopes()).containsExactly(GuardScope.server("lobby"), GuardScope.set("hub"));
        assertThat(unknown.newRegionScope()).isEqualTo(GuardScope.server("lobby"));
        assertThat(problems).isNotEmpty();
    }
}

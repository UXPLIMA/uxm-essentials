package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog.GameRuleType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BukkitGameRuleCatalogTest {

    private final GameRuleCatalog catalog = new BukkitGameRuleCatalog();

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void typeOfResolvesEachReportedRuleToBooleanOrInteger() {
        List<String> names = catalog.names();
        assertThat(names).isNotEmpty();
        for (String name : names) {
            assertThat(catalog.typeOf(name))
                    .as("type of %s", name)
                    .isPresent()
                    .get()
                    .isIn(GameRuleType.BOOLEAN, GameRuleType.INTEGER);
        }
    }

    @Test
    void typeOfReportsBooleanAndIntegerRulesByTheirReportedNames() {
        Optional<String> aBooleanRule = catalog.names().stream()
                .filter(name -> name.contains("keep") || name.contains("mobGriefing") || name.contains("mob_griefing"))
                .findFirst();
        Optional<String> anIntegerRule = catalog.names().stream()
                .filter(name -> name.contains("randomTick") || name.contains("random_tick"))
                .findFirst();
        assertThat(aBooleanRule).as("a boolean rule must be present").isPresent();
        assertThat(anIntegerRule).as("an integer rule must be present").isPresent();
        assertThat(catalog.typeOf(aBooleanRule.orElseThrow())).contains(GameRuleType.BOOLEAN);
        assertThat(catalog.typeOf(anIntegerRule.orElseThrow())).contains(GameRuleType.INTEGER);
    }

    @Test
    void typeOfReturnsEmptyForUnknownRule() {
        assertThat(catalog.typeOf("nopeNotARule")).isEmpty();
    }

    @Test
    void namesAreSortedDistinctAndNonEmpty() {
        List<String> names = catalog.names();
        assertThat(names).isNotEmpty().doesNotHaveDuplicates().isSorted();
    }
}

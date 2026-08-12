package com.uxplima.uxmessentials.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DocsModelBuilderTest {

    private static DocsData.Module module(String id) {
        return DocsModelBuilder.build().stream()
                .filter(m -> m.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no module " + id));
    }

    @Test
    void carriesEveryRegisteredModuleOnceInAlphabeticalOrder() {
        List<String> ids =
                DocsModelBuilder.build().stream().map(DocsData.Module::id).toList();

        assertThat(ids).hasSize(34).doesNotHaveDuplicates().isSorted();
    }

    @Test
    void namesTheConfigFileAndWhetherTheModuleShipsOn() {
        DocsData.Module homes = module("homes");

        assertThat(homes.configPath()).isEqualTo("modules/homes/config.conf");
        assertThat(homes.enabledByDefault()).isTrue();
    }

    @Test
    void carriesACommandWithItsAliasesAndTheDescriptionOfItsGuardNode() {
        DocsData.Command home = module("homes").commands().stream()
                .filter(c -> c.literal().equals("home"))
                .findFirst()
                .orElseThrow();

        assertThat(home.aliases()).contains("h", "homes");
        assertThat(home.permission()).isEqualTo("uxmessentials.home.use");
        assertThat(home.description()).isNotBlank();
    }

    @Test
    void carriesOnlyThePermissionsAndPlaceholdersTheModuleOwns() {
        DocsData.Module economy = module("economy");

        assertThat(economy.permissions()).isNotEmpty().allMatch(p -> p.node().startsWith("uxmessentials."));
        assertThat(economy.placeholders()).isNotEmpty();
        assertThat(module("homes").placeholders()).noneMatch(p -> p.key().startsWith("balance"));
    }

    @Test
    void findsTheCommandsAModuleRegistersFromItsOwnWiringToo() {
        assertThat(module("trade").commands())
                .extracting(DocsData.Command::literal)
                .contains("trade");
        assertThat(module("vanish").commands())
                .extracting(DocsData.Command::literal)
                .contains("vanish");
        assertThat(module("survival").commands())
                .extracting(DocsData.Command::literal)
                .contains("treefeller", "veinminer");
    }

    @Test
    void listsACommandOnceEvenWhenTheModuleAlsoPublishesItAsASpec() {
        for (DocsData.Module module : DocsModelBuilder.build()) {
            assertThat(module.commands())
                    .as("%s lists a command twice", module.id())
                    .extracting(DocsData.Command::literal)
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void readsTheSettingsOutOfTheShippedConfigWithoutTheEnabledSwitch() {
        assertThat(module("kits").settings())
                .isNotEmpty()
                .noneMatch(s -> s.key().equals("enabled"))
                .anyMatch(s -> !s.description().isBlank());
    }

    @Test
    void findsTheGuardNodeOfACommandThatNamesItSomethingOtherThanPermission() {
        DocsData.Command setrank = module("ranks").commands().stream()
                .filter(c -> c.literal().equals("setrank"))
                .findFirst()
                .orElseThrow();

        assertThat(setrank.permission()).isEqualTo("uxmessentials.ranks.admin");
        assertThat(setrank.description()).isNotBlank();
    }

    @Test
    void startsACommandDescriptionAtItsVerbRatherThanRepeatingTheCommand() {
        DocsData.Command home = module("homes").commands().stream()
                .filter(c -> c.literal().equals("home"))
                .findFirst()
                .orElseThrow();

        assertThat(home.description()).doesNotStartWith("/home to ").startsWith("Open");
    }
}

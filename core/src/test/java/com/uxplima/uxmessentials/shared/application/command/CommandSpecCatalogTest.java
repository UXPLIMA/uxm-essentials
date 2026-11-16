package com.uxplima.uxmessentials.shared.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.permission.PermissionCatalog;
import com.uxplima.uxmessentials.testing.RepositoryFiles;
import org.junit.jupiter.api.Test;

/**
 * Holds the command catalogue to what it publishes: every command a node the permission catalogue knows, every
 * literal claimed once, and a description line a player reads for every command and for nothing else.
 */
class CommandSpecCatalogTest {

    @Test
    void everyCommandIsGatedByANodeThePermissionCatalogueDeclares() {
        List<String> unknown = CommandSpecCatalog.all().stream()
                .filter(spec -> PermissionCatalog.find(spec.permission()).isEmpty())
                .map(spec -> "/" + spec.name() + " -> " + spec.permission())
                .toList();

        assertThat(unknown)
                .as("a command's node is declared in a *Permissions table")
                .isEmpty();
    }

    @Test
    void noTwoCommandsClaimTheSameWord() {
        Map<String, String> owners = new HashMap<>();
        List<String> clashes = new ArrayList<>();
        for (CommandSpec spec : CommandSpecCatalog.all()) {
            for (String literal : spec.literals()) {
                String owner = owners.putIfAbsent(literal, spec.id());
                if (owner != null && !owner.equals(spec.id())) {
                    clashes.add(literal + " is claimed by " + owner + " and " + spec.id());
                }
            }
        }

        assertThat(clashes)
                .as("the catalog resolver keeps the first claim and drops the rest, so a shared word is a dead alias")
                .isEmpty();
    }

    @Test
    void everyIdIsListedOnce() {
        List<String> ids =
                CommandSpecCatalog.all().stream().map(CommandSpec::id).toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void everyCommandHasTheDescriptionLineAPlayerReadsAndNothingElseDoes() {
        Set<String> described = RepositoryFiles.commandDescriptions().keySet();
        Set<String> listed =
                CommandSpecCatalog.all().stream().map(CommandSpec::id).collect(Collectors.toSet());

        assertThat(described)
                .as(
                        "every command in CommandSpecCatalog has a describe.<id> line in %s",
                        RepositoryFiles.ENGLISH_MESSAGES)
                .containsAll(listed);
        assertThat(listed)
                .as(
                        "every describe.<id> line in %s names a command CommandSpecCatalog lists",
                        RepositoryFiles.ENGLISH_MESSAGES)
                .containsAll(described);
    }

    @Test
    void aRenamedCommandIsFoundByItsCatalogIdAndListedUnderItsName() {
        CommandSpec spec = CommandSpecCatalog.find("twofactor").orElseThrow();

        assertThat(spec.name()).isEqualTo("2fa");
        assertThat(spec.literals()).containsExactly("2fa", "twofactor");
        assertThat(CommandSpecCatalog.forArea("security")).contains(spec);
    }

    @Test
    void theKernelsCommandsComeFirst() {
        assertThat(CommandSpecCatalog.areas()).first().isEqualTo("shared");
        assertThat(CommandSpecCatalog.forArea("shared"))
                .extracting(CommandSpec::name)
                .contains("uxmess", "help", "lang");
    }

    @Test
    void aCommandIsALowerCaseWordGatedInsideThePluginsSpace() {
        ModuleId homes = ModuleId.of("homes");

        assertThatThrownBy(() -> CommandSpec.of(homes, "Home", "uxmessentials.home.use"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommandSpec.of(homes, "home", "other.home.use"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommandSpec.of(homes, "home", "uxmessentials.home.use", "h", "h"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

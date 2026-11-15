package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The update of an operator's config file: what a new version appends to it, and what it must leave alone. Each
 * case feeds the three texts the plugin compares (this version's default, the default the file was last
 * reconciled against, and the operator's own file) and reads the file the way the plugin would after the append.
 */
class BundledDefaultsMergeTest {

    private static final String PREVIOUS_TELEPORT = """
            teleport {
              # Seconds a player stands still before the teleport.
              delay = 3
            }
            """;

    private static final String BUNDLED_TELEPORT = """
            teleport {
              # Seconds a player stands still before the teleport.
              delay = 3
              # Seconds a player cannot be hurt after arriving.
              grace = 5
            }
            """;

    @Test
    void aSettingTheUpdateAddedArrivesWithTheCommentThatExplainsIt() throws ConfigurateException {
        String operator = """
                teleport {
                  delay = 3
                }
                """;

        Optional<String> added = BundledDefaultsMerge.newSettings(BUNDLED_TELEPORT, PREVIOUS_TELEPORT, operator);

        assertThat(added)
                .hasValueSatisfying(fragment -> assertThat(fragment)
                        .contains("Seconds a player cannot be hurt after arriving.")
                        .contains("grace"));
        assertThat(read(appended(operator, added)).node("teleport", "grace").getInt())
                .isEqualTo(5);
    }

    @Test
    void aValueTheOperatorChangedIsStillTheirsAfterTheAppend() throws ConfigurateException {
        String operator = """
                teleport {
                  delay = 10
                }
                """;

        Optional<String> added = BundledDefaultsMerge.newSettings(BUNDLED_TELEPORT, PREVIOUS_TELEPORT, operator);

        CommentedConfigurationNode merged = read(appended(operator, added));
        assertThat(merged.node("teleport", "delay").getInt()).isEqualTo(10);
        assertThat(merged.node("teleport", "grace").getInt()).isEqualTo(5);
    }

    @Test
    void aFileThatAlreadyHasEverySettingGetsNothingAppended() throws ConfigurateException {
        String operator = """
                teleport {
                  delay = 10
                  grace = 0
                }
                """;

        assertThat(BundledDefaultsMerge.newSettings(BUNDLED_TELEPORT, PREVIOUS_TELEPORT, operator))
                .isEmpty();
    }

    @Test
    void aSettingTheOperatorDeletedIsNotPutBack() throws ConfigurateException {
        String operator = """
                teleport {
                  grace = 5
                }
                """;

        assertThat(BundledDefaultsMerge.newSettings(BUNDLED_TELEPORT, BUNDLED_TELEPORT, operator))
                .isEmpty();
    }

    @Test
    void aBlockTheOperatorTurnedIntoAPlainValueIsLeftAlone() throws ConfigurateException {
        String operator = """
                teleport = false
                """;

        assertThat(BundledDefaultsMerge.newSettings(BUNDLED_TELEPORT, PREVIOUS_TELEPORT, operator))
                .isEmpty();
    }

    @Test
    void aStorageSettingStillWrittenUnderItsFormerNameIsNotWrittenAgainUnderTheNewOne() throws ConfigurateException {
        String previous = """
                storage {
                  backend = "sqlite"
                  read-pool-size = 8
                }
                """;
        String bundled = """
                storage {
                  type = "sqlite"
                  pool-size = 8
                }
                """;
        String operator = """
                storage {
                  backend = "mysql"
                  read-pool-size = 16
                }
                """;

        assertThat(BundledDefaultsMerge.newSettings(bundled, previous, operator))
                .isEmpty();
    }

    @Test
    void aRenamedStorageSettingTheOperatorNeverWroteArrivesUnderItsNewName() throws ConfigurateException {
        String previous = """
                storage {
                  file = "uxmessentials.db"
                }
                """;
        String bundled = """
                storage {
                  type = "sqlite"
                  file = "uxmessentials.db"
                }
                """;
        String operator = """
                storage {
                  file = "server.db"
                }
                """;

        Optional<String> added = BundledDefaultsMerge.newSettings(bundled, previous, operator);

        CommentedConfigurationNode merged = read(appended(operator, added));
        assertThat(merged.node("storage", "type").getString()).isEqualTo("sqlite");
        assertThat(merged.node("storage", "file").getString()).isEqualTo("server.db");
    }

    @Test
    void theTablePrefixIsNeverAddedToAnExistingFile() throws ConfigurateException {
        String previous = """
                storage {
                  type = "sqlite"
                }
                """;
        String bundled = """
                storage {
                  type = "sqlite"
                  # Written in front of every table.
                  table-prefix = "uxmessentials_"
                  # Moving from the SQLite file to a network database.
                  copy-from-file = false
                }
                """;
        String operator = """
                storage {
                  type = "mysql"
                }
                """;

        Optional<String> added = BundledDefaultsMerge.newSettings(bundled, previous, operator);

        CommentedConfigurationNode merged = read(appended(operator, added));
        assertThat(merged.node("storage", "table-prefix").virtual()).isTrue();
        assertThat(merged.node("storage", "copy-from-file").getBoolean(true)).isFalse();
        assertThat(merged.node("storage", "type").getString()).isEqualTo("mysql");
    }

    @Test
    void aMessageTheOperatorNeverTouchedIsBroughtToTheNewWording() {
        String previous = """
                "home.set" = "Home set."
                "home.gone" = "That home is gone."
                """;
        String bundled = """
                "home.set" = "Your home is set."
                "home.gone" = "That home no longer exists."
                """;
        String operator = """
                "home.set" = "Home set."
                "home.gone" = "<red>Nope, no such home."
                """;

        assertThat(BundledDefaultsMerge.updatedLines(bundled, previous, operator))
                .isEqualTo("""
                        "home.set" = "Your home is set."
                        "home.gone" = "<red>Nope, no such home."
                        """);
    }

    /** The operator's file with the fragment appended, as the plugin writes it back. */
    private static String appended(String operator, Optional<String> added) {
        return added.map(fragment -> operator + "\n" + fragment).orElse(operator);
    }

    private static CommentedConfigurationNode read(String hocon) throws ConfigurateException {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(hocon)))
                .build()
                .load();
    }
}

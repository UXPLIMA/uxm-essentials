package com.uxplima.uxmessentials.bootstrap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.stream.Stream;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.junit.jupiter.api.Test;

/**
 * Keeps dependencies pointing inward. The compiler only stops a module from naming what is missing from its class
 * path, so a domain type reaching into the application layer of its own module, or a library added to core's build
 * file, compiles without complaint; the compiled classes of every module this test sees are read instead.
 *
 * <p>Work scheduled around the Scheduler port and permission nodes written outside the catalogue have their own
 * source guards, so they are not repeated here.
 */
class LayerDependenciesTest {

    private static final String BASE = "com.uxplima.uxmessentials";

    /** Where the outside world lives: the server, the libraries the adapters bind to, and the platform logger. */
    private static final String[] FRAMEWORKS = {
        "org.bukkit..",
        "io.papermc..",
        "com.destroystokyo..",
        "net.minecraft..",
        "net.kyori..",
        "org.jooq..",
        "com.velocitypowered..",
        "io.lettuce..",
        "org.spongepowered.configurate..",
        "org.slf4j..",
        "com.uxplima.uxmlib..",
        "java.util.logging..",
        "java.sql..",
        "javax.sql..",
    };

    /** The adapter side: inbound and outbound adapters, storage, the importers and the wiring. */
    private static final String[] ADAPTERS = {
        "..adapter..", BASE + ".bootstrap..", BASE + ".persistence..", BASE + ".migration..",
    };

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    /** The classes compiled from the core module, whichever package they sit in. */
    private static final DescribedPredicate<JavaClass> IN_CORE = DescribedPredicate.describe(
            "are compiled from the core module",
            type -> type.getSource()
                    .map(source -> source.getUri().toString().contains("/core/build/"))
                    .orElse(false));

    @Test
    void theDomainKnowsNothingButItself() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(concat(new String[] {"..application.."}, ADAPTERS, FRAMEWORKS))
                .check(MAIN);
    }

    @Test
    void theApplicationReachesNoAdapterAndNoFramework() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(concat(ADAPTERS, FRAMEWORKS))
                .check(MAIN);
    }

    @Test
    void coreRunsWithoutTheServer() {
        noClasses()
                .that(IN_CORE)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(concat(ADAPTERS, FRAMEWORKS))
                .check(MAIN);
    }

    /** A line on standard out or a stack trace on standard error bypasses the logger and the operator's log file. */
    @Test
    void nothingWritesToTheStandardStreams() {
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(MAIN);
    }

    /** Colours come from the theme through MiniMessage; a legacy colour code bypasses both. */
    @Test
    void nothingUsesLegacyColourCodes() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.bukkit.ChatColor")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("net.md_5.bungee.api.ChatColor")
                .check(MAIN);
    }

    private static String[] concat(String[]... groups) {
        return Stream.of(groups).flatMap(Stream::of).toArray(String[]::new);
    }
}

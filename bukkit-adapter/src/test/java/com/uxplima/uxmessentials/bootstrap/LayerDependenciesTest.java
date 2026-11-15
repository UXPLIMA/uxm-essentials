package com.uxplima.uxmessentials.bootstrap;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.net.URL;
import java.security.CodeSource;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.library.GeneralCodingRules;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
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

    /** The core module's own classes, read from wherever its classes are on this class path. */
    private static final JavaClasses CORE =
            new ClassFileImporter().importLocations(Set.of(Location.of(locationOf(MessageKey.class))));

    /**
     * What core may use: the JDK (its crypto included) without its logger and JDBC, the nullness and immutability
     * annotations, and the plugin's own packages short of the adapter side. Anything else is a library core would
     * have to ship with.
     */
    private static final DescribedPredicate<JavaClass> CORE_MAY_USE = resideInAnyPackage(
                    "java..", "javax.crypto..", "org.jspecify..", "com.google.errorprone.annotations..", BASE + "..")
            .and(not(resideInAnyPackage(concat(new String[] {"java.util.logging..", "java.sql.."}, ADAPTERS))));

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
        classes().should().onlyDependOnClassesThat(CORE_MAY_USE).check(CORE);
    }

    /** The provider SDKs stay in the adapters that bind to them; the economy rules never name one. */
    @Test
    void economyDomainHasNoProviderSdk() {
        noClasses()
                .that()
                .resideInAnyPackage(BASE + ".economy.domain..", BASE + ".economy.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("net.milkbowl.vault..", "me.lokka30.treasury..")
                .check(MAIN);
    }

    /** Value equality backs bus de-duplication, codec round trips and the tests that compare events. */
    @Test
    void domainEventConcreteImplementationsAreRecords() {
        classes()
                .that()
                .implement(DomainEvent.class)
                .and()
                .areNotInterfaces()
                .and()
                .doNotHaveModifier(JavaModifier.ABSTRACT)
                .should()
                .beRecords()
                .check(MAIN);
    }

    /** A published signature that named an internal type would turn a refactor into a break for other plugins. */
    @Test
    void publishedApiNamesNoInternalType() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".api..")
                .should()
                .dependOnClassesThat(resideInAPackage(BASE + "..").and(not(resideInAPackage(BASE + ".api.."))))
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

    /** The class directory or jar {@code type} was loaded from, so the IDE and Gradle outputs both resolve. */
    private static URL locationOf(Class<?> type) {
        CodeSource source = Objects.requireNonNull(
                type.getProtectionDomain().getCodeSource(), () -> "no code source for " + type.getName());
        return source.getLocation();
    }

    private static String[] concat(String[]... groups) {
        return Stream.of(groups).flatMap(Stream::of).toArray(String[]::new);
    }
}

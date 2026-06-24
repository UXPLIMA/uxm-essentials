package com.uxplima.uxmessentials.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Compiler-backstop architecture fences. They run as ordinary JUnit 5 tests on every {@code check}.
 *
 * <p>Each rule carries {@code allowEmptyShould(true)} so it passes vacuously while a layer or context
 * is still empty — ArchUnit 1.x otherwise fails a rule that matches zero classes. The flag is dropped
 * per rule once the matching layer fills. This phase fills only the kernel module framework and the
 * bootstrap, so the domain/application-purity rules and the JavaPlugin-containment rule already have
 * teeth.
 */
@AnalyzeClasses(packages = "com.uxplima.uxmessentials")
class ArchitectureTest {

    // Domain layer (:core) imports no Bukkit / Paper / Adventure.
    @ArchTest
    static final ArchRule domainHasNoBukkit = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "net.kyori..")
            .allowEmptyShould(true);

    // Application layer (:core) imports no Bukkit / Paper / Adventure.
    @ArchTest
    static final ArchRule applicationHasNoBukkit = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "net.kyori..")
            .allowEmptyShould(true);

    // The :core module is also free of SLF4J and infrastructure libraries — the ports stay pure.
    @ArchTest
    static final ArchRule domainAndApplicationHaveNoInfrastructure = noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.slf4j..",
                    "com.zaxxer..",
                    "org.jooq..",
                    "com.github.benmanes..",
                    "org.spongepowered.configurate..")
            .allowEmptyShould(true);

    // Only bootstrap may depend on the concrete JavaPlugin class. A future contributor reaching for
    // JavaPlugin (or JavaPlugin.getInstance) outside bootstrap breaks constructor-injection here.
    @ArchTest
    static final ArchRule bootstrapIsTheOnlyPlaceWithJavaPlugin = noClasses()
            .that()
            .resideOutsideOfPackage("..bootstrap..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.bukkit.plugin.java.JavaPlugin")
            .allowEmptyShould(true);

    // BukkitScheduler is forbidden everywhere — scheduling goes through the Folia-aware Scheduler
    // port so the plugin stays Folia-compatible.
    @ArchTest
    static final ArchRule noClassDependsOnBukkitScheduler = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.bukkit.scheduler.BukkitScheduler")
            .allowEmptyShould(true);

    // The economy domain/application is provider-agnostic: it models money, not Vault or Treasury. The
    // SDK types are confined to the outbound adapter packages (economy.adapter.treasury / .vault); a
    // contributor reaching for a Vault/Treasury type in economy.domain or economy.application breaks the
    // single-seam design here (docs/11-economy-integration.md §5).
    @ArchTest
    static final ArchRule economyDomainHasNoProviderSdk = noClasses()
            .that()
            .resideInAnyPackage("..economy.domain..", "..economy.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("net.milkbowl.vault..", "me.lokka30.treasury..")
            .allowEmptyShould(true);

    // The menu engine's spec model and evaluation are the pure core: plain JUnit can exercise them
    // without a server. Keeping them free of Bukkit / Paper / NMS is what makes that possible.
    @ArchTest
    static final ArchRule menuPureCoreHasNoBukkit = noClasses()
            .that()
            .resideInAnyPackage("..gui.menu.spec..", "..gui.menu.eval..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "net.minecraft..")
            .because("the menu engine's spec model and evaluation must stay pure for plain-JUnit testing");

    // Only the Menus facade and the binding registries are the engine's public surface. The render and
    // runtime internals stay private to the engine, so features wire behaviour through the facade alone.
    // Two packages are exempt for the same reason they are everywhere else: bootstrap is the composition
    // root that constructs the engine (it is not a feature), and the engine's own tests under
    // shared.menu.. legitimately exercise render/runtime directly.
    @ArchTest
    static final ArchRule menuInternalsAreNotUsedOutsideTheEngine = noClasses()
            .that()
            .resideOutsideOfPackages("..gui.menu..", "..bootstrap..", "com.uxplima.uxmessentials.shared.menu..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..gui.menu.render..", "..gui.menu.runtime..")
            .because(
                    "only the Menus facade and MenuBindings are the public surface; render/runtime internals stay private to the engine");
}

package com.uxplima.uxmessentials.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
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

    // The engine's public surface is the Menus facade, the MenuBindings registries, and the two context
    // types a binding lambda is handed — MenuContext (condition/placeholder/list) and MenuActionContext
    // (action). A feature wires behaviour by reading those contexts, so they are public by contract even
    // though they live alongside the runtime. Everything else under render/ and runtime/ — the holder, the
    // click listener, the refresh task — is the engine's private machinery and stays off-limits outside it.
    // Two packages are exempt for the same reason they are everywhere else: bootstrap is the composition
    // root that constructs the engine (it is not a feature), and the engine's own tests under
    // shared.menu.. legitimately exercise render/runtime directly.
    @ArchTest
    static final ArchRule menuInternalsAreNotUsedOutsideTheEngine = noClasses()
            .that()
            .resideOutsideOfPackages("..gui.menu..", "..bootstrap..", "com.uxplima.uxmessentials.shared.menu..")
            .and(areProductionClasses())
            .should()
            .dependOnClassesThat(menuInternals())
            .because("only the Menus facade, the MenuBindings registries, and the MenuContext/MenuActionContext a "
                    + "binding reads are the public surface; the holder, listener, refresh task and renderer stay "
                    + "private to the engine");

    // Every spec-driven menu renders through the engine (Menus / MenuBindings / holder / listener); none of them
    // touch uxmLib's GUI library directly. The only production classes that legitimately depend on
    // com.uxplima.uxmlib.gui are five non-menu leaves, and they stay that way:
    //   - vaults VaultView and itemworld DisposalCommand are real item-STORAGE inventories (uxmLib StorageGui):
    //     players put and take items, contents persist — these are not menus.
    //   - the shared AnvilTextBackend and its TextInputInstaller are the anvil TEXT-INPUT seam (uxmLib
    //     com.uxplima.uxmlib.gui.anvil) — the single runtime-neutral leaf the whole engine reuses for typed input.
    //   - bootstrap PluginModule is the Guis.install(...) site: the uxmLib GUI runtime must stay installed for the
    //     storage and anvil leaves above.
    // Any other production class reaching for com.uxplima.uxmlib.gui would be a spec menu that slipped off the
    // engine; this fence fails until it is migrated, rather than letting it bypass the engine silently.
    @ArchTest
    static final ArchRule onlyStorageAndAnvilLeavesUseUxmlibGui = noClasses()
            .that()
            .resideInAPackage("..uxmessentials..")
            .and(areProductionClasses())
            .and(areNotAllowedUxmlibGuiLeaves())
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.uxplima.uxmlib.gui..")
            .because("spec-driven menus must render through the engine; only the item-storage inventories "
                    + "(VaultView, DisposalCommand), the anvil text-input seam (AnvilTextBackend, "
                    + "TextInputInstaller) and the Guis.install site (PluginModule) may touch uxmLib's GUI library");

    /**
     * The five non-menu leaves allowed to depend on uxmLib's GUI library, named by fully qualified name so this
     * predicate itself adds no dependency on them. Two are item-storage inventories ({@code VaultView},
     * {@code DisposalCommand}, built on uxmLib's {@code StorageGui}), two are the anvil text-input seam
     * ({@code AnvilTextBackend}, {@code TextInputInstaller}, built on {@code com.uxplima.uxmlib.gui.anvil}), and one
     * is the {@code Guis.install} site ({@code PluginModule}). Every spec-driven menu renders through the engine
     * instead, so this allow-list must stay exactly these five.
     *
     * <p>A leaf's nested members ({@code VaultView.OpenWindow}, {@code TextInputInstaller.Installed},
     * {@code PluginModule.ContextLinks}) carry the dependency too — a held {@code StorageGui} or {@code AnvilInput}
     * surfaces as a nested record's field — so the match is on the top-level enclosing class, not the exact nested
     * name. That keeps the allow-list to these five top-level leaves while still covering their inner classes.
     */
    private static DescribedPredicate<JavaClass> areNotAllowedUxmlibGuiLeaves() {
        java.util.Set<String> allowed = java.util.Set.of(
                "com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView",
                "com.uxplima.uxmessentials.itemworld.adapter.inbound.command.DisposalCommand",
                "com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.AnvilTextBackend",
                "com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputInstaller",
                "com.uxplima.uxmessentials.bootstrap.di.PluginModule");
        return DescribedPredicate.describe("are not the allowed uxmLib-GUI leaves", javaClass -> {
            String fullName = javaClass.getFullName();
            int nested = fullName.indexOf('$');
            String topLevel = nested < 0 ? fullName : fullName.substring(0, nested);
            return !allowed.contains(topLevel);
        });
    }

    /**
     * The engine's private machinery: everything under {@code render/} plus the runtime internals, but not the
     * two public context types a binding lambda reads. Naming the runtime internals one by one (by their fully
     * qualified names, so this predicate itself does not depend on those classes) is what lets feature wiring
     * register a binding through {@code MenuContext}/{@code MenuActionContext} while keeping the holder, listener
     * and refresh task encapsulated.
     */
    private static DescribedPredicate<JavaClass> menuInternals() {
        String runtime = "com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.";
        java.util.Set<String> runtimeInternals = java.util.Set.of(
                runtime + "MenuHolder", runtime + "MenuListener", runtime + "MenuRefresh", runtime + "Cancellable");
        return JavaClass.Predicates.resideInAPackage("..gui.menu.render..")
                .or(DescribedPredicate.describe(
                        "are menu runtime internals", javaClass -> runtimeInternals.contains(javaClass.getFullName())))
                .as("menu engine render/runtime internals");
    }

    /**
     * Production classes only — the architecture tests and their nested helpers legitimately wire the engine to
     * exercise it, so this rule must not flag a test that constructs the renderer or listener for a fixture.
     */
    private static DescribedPredicate<JavaClass> areProductionClasses() {
        return DescribedPredicate.describe(
                "are production classes", javaClass -> !javaClass.getName().contains("Test"));
    }
}

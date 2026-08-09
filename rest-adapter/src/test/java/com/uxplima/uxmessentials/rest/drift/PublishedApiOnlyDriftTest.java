package com.uxplima.uxmessentials.rest.drift;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The one rule that makes this add-on worth having: it may use the published developer API and nothing else.
 *
 * <p>The build already arranges it, since neither {@code :core} nor {@code :bukkit-adapter} is on this module's
 * compile classpath. This says so out loud, so adding the dependency to get one endpoint working fails here rather
 * than passing quietly.
 *
 * <p>What it buys is a running proof rather than a promise. Every endpoint in this jar is something a third-party
 * plugin could have written with the same imports, so a hole in the published API shows up as an endpoint that
 * cannot be built, and gets filled in the API where everyone gets it.
 */
@AnalyzeClasses(
        packages = "com.uxplima.uxmessentials.rest",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class PublishedApiOnlyDriftTest {

    @ArchTest
    static final ArchRule theAddOnUsesThePublishedApiAndNothingElse = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.uxplima.uxmessentials.shared..",
                    "com.uxplima.uxmessentials.bootstrap..",
                    "com.uxplima.uxmessentials.economy..",
                    "com.uxplima.uxmessentials.homes..",
                    "com.uxplima.uxmessentials.warps..",
                    "com.uxplima.uxmessentials.playerwarps..",
                    "com.uxplima.uxmessentials.kits..",
                    "com.uxplima.uxmessentials.vaults..",
                    "com.uxplima.uxmessentials.moderation..",
                    "com.uxplima.uxmessentials.presence..",
                    "com.uxplima.uxmessentials.playerstate..",
                    "com.uxplima.uxmessentials.teleport..",
                    "com.uxplima.uxmessentials.worlds..",
                    "com.uxplima.uxmessentials.vote..",
                    "com.uxplima.uxmessentials.messaging..",
                    "com.uxplima.uxmessentials.loader..")
            .because("the REST add-on compiles against the published developer API only, so every endpoint in it is"
                    + " one a third-party plugin could have written");
}

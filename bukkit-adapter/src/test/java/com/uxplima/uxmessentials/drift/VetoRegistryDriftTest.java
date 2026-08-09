package com.uxplima.uxmessentials.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridges;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.VetoRegistry;
import com.uxplima.uxmessentials.shared.domain.DomainProposal;
import org.junit.jupiter.api.Test;

/**
 * Which operations a third-party plugin can refuse, pinned as a list somebody can read.
 *
 * <p>A proposal with no mapping behind it is worse than useless: the use case pays for the question, the answer is
 * always yes, and every reader of the code believes the operation is vetoable when it is not. So the two halves are
 * compared here, the same way the notification bridge's coverage is.
 *
 * <p>The list is deliberately short and stays short. A veto point is a promise that the operation can be refused
 * cleanly, and most of what the plugin does cannot: a mail already read, a punishment already served, a warp
 * already walked to. Adding one means proving nothing is written and nothing is charged before the question.
 */
class VetoRegistryDriftTest {

    /**
     * Every vetoable operation, by the proposal that asks. Changing this list changes the published surface, so it
     * is written down rather than derived: a reviewer sees the diff and can ask whether the new one is really
     * refusable without leaving something half-done.
     */
    private static final Set<String> VETOABLE = Set.of(
            "com.uxplima.uxmessentials.homes.domain.event.HomeCreating",
            "com.uxplima.uxmessentials.homes.domain.event.HomeDeleting",
            "com.uxplima.uxmessentials.homes.domain.event.HomeRelocating",
            "com.uxplima.uxmessentials.teleport.domain.event.PlayerTeleporting",
            "com.uxplima.uxmessentials.warps.domain.event.WarpCreating",
            "com.uxplima.uxmessentials.warps.domain.event.WarpDeleting",
            "com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpCreating",
            "com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpDeleting",
            "com.uxplima.uxmessentials.kits.domain.event.KitClaiming");

    @Test
    void everyProposalTheDomainDefinesHasAPreEventBehindIt() {
        Set<String> unmapped = new TreeSet<>(allProposalNames());
        unmapped.removeAll(registeredNames());

        assertThat(unmapped)
                .as("these proposals reach no published pre-event, so asking about them always answers yes: "
                        + "map them in their context's *EventBridges.registerVetoes, or delete them")
                .isEmpty();
    }

    @Test
    void theRegisteredSetIsTheDocumentedSet() {
        assertThat(registeredNames())
                .as("the vetoable surface changed; update VETOABLE and the developer docs together")
                .isEqualTo(new TreeSet<>(VETOABLE));
    }

    @Test
    void everyDocumentedProposalStillExists() {
        assertThat(allProposalNames())
                .as("VETOABLE names a proposal the domain no longer has")
                .containsAll(VETOABLE);
    }

    private static Set<String> registeredNames() {
        VetoRegistry registry = new VetoRegistry();
        EventBridges.installAllVetoes(registry);
        return registry.vetoable().stream().map(Class::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> allProposalNames() {
        // Jars are deliberately left in: the domain reaches this module as :core's jar, so excluding them would
        // make this guard pass by finding nothing.
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.uxplima.uxmessentials");
        return classes.stream()
                .filter(type -> type.isAssignableTo(DomainProposal.class))
                .filter(type -> !type.isInterface())
                .filter(type -> !Modifier.isAbstract(type.reflect().getModifiers()))
                .map(JavaClass::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void theProposalListItselfIsNotEmpty() {
        // The same insurance the event coverage guard carries: a guard that silently stops seeing the domain would
        // pass by having nothing to check.
        assertThat(List.copyOf(allProposalNames())).hasSameSizeAs(VETOABLE);
    }
}

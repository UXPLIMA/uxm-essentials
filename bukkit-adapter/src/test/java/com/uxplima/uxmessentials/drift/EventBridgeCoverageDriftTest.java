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
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridges;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import org.junit.jupiter.api.Test;

/**
 * Every domain fact the plugin publishes has to reach the developer API, or be a deliberate exception.
 *
 * <p>This is the guard that makes "the API covers everything" true rather than aspirational. Adding a domain event is
 * the easy half of a feature and it is the half people remember; wiring it through to the published surface is the
 * half that gets forgotten, and nobody notices, because a missing event looks exactly like an event that never
 * fired. So the two lists are compared here instead: add a fact and this fails until it is either bridged or
 * written down below as one that should not be.
 */
class EventBridgeCoverageDriftTest {

    /**
     * Facts that stay internal on purpose. Each needs a reason, and "we did not get round to it" is not one: an
     * entry here is a promise that publishing it would be wrong, not that it is pending.
     */
    private static final Set<String> DELIBERATELY_INTERNAL = Set.of();

    @Test
    void everyDomainFactIsBridgedToAPublishedEvent() {
        Set<String> bridged = new TreeSet<>(bridgedNames());
        Set<String> unbridged = new TreeSet<>(allFactNames());
        unbridged.removeAll(bridged);
        unbridged.removeAll(DELIBERATELY_INTERNAL);

        assertThat(unbridged)
                .as("these domain facts reach no published Bukkit event: bridge them in their context's "
                        + "*EventBridges class, or add them to DELIBERATELY_INTERNAL with a reason")
                .isEmpty();
    }

    @Test
    void nothingIsListedAsInternalWhileAlsoBeingBridged() {
        // The two lists drifting apart the other way is quieter and just as wrong: an exemption that no longer
        // applies reads as a decision somebody made, when really it is a leftover.
        Set<String> contradictory = new TreeSet<>(DELIBERATELY_INTERNAL);
        contradictory.retainAll(bridgedNames());

        assertThat(contradictory)
                .as("these are bridged and also listed as internal; drop them from DELIBERATELY_INTERNAL")
                .isEmpty();
    }

    @Test
    void everyBridgedTypeIsStillADomainFact() {
        Set<String> known = allFactNames();

        assertThat(bridgedNames())
                .as("the registry bridges something the domain no longer publishes")
                .isSubsetOf(known);
    }

    private static Set<String> bridgedNames() {
        EventBridgeRegistry registry = new EventBridgeRegistry();
        EventBridges.installAll(registry);
        return registry.bridged().stream().map(Class::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Every concrete domain event in the production classes, which is what a bridge has to account for. */
    private static Set<String> allFactNames() {
        // Jars are deliberately left in: the domain reaches this module as :core's jar, so excluding them would
        // make this guard pass by finding nothing, which is exactly the failure it exists to prevent.
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.uxplima.uxmessentials");
        return classes.stream()
                .filter(type -> type.isAssignableTo(DomainEvent.class))
                .filter(type -> !type.isInterface())
                .filter(type -> !Modifier.isAbstract(type.reflect().getModifiers()))
                .map(JavaClass::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void theFactListItselfIsNotEmpty() {
        // Cheap insurance: if the importer ever stops seeing the domain, the first test would pass by finding
        // nothing to complain about, which is the worst way for a coverage guard to fail.
        assertThat(List.copyOf(allFactNames())).hasSizeGreaterThan(50);
    }
}

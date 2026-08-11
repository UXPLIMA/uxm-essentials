package com.uxplima.uxmessentials.rest.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.uxplima.uxmessentials.api.bukkit.event.UxmCancellableEvent;
import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import com.uxplima.uxmessentials.rest.bridge.EventJson;
import com.uxplima.uxmessentials.rest.bridge.EventNames;
import com.uxplima.uxmessentials.rest.bridge.PublishedEvents;
import org.junit.jupiter.api.Test;

/**
 * Keeps the streamed catalogue in step with the events that exist.
 *
 * <p>An event added upstream and forgotten here would never arrive, and nothing about the running server would say
 * so: the endpoint would answer, the subscription would be accepted, and the event simply would not come. That is
 * the failure this guard exists for, so the list is compared against every published event class rather than
 * trusted.
 */
class RestEventCatalogueDriftTest {

    private static final String EVENTS_PACKAGE = "com.uxplima.uxmessentials.api.bukkit.event";

    private static final Path GOLDEN = Path.of("src", "test", "resources", "rest-events.txt");
    private static final Path ACTUAL = Path.of("build", "rest-events.actual.txt");

    private static final JavaClasses PUBLISHED = new ClassFileImporter().importPackages(EVENTS_PACKAGE);

    @Test
    void everyPublishedEventThatIsNotCancellableIsStreamed() {
        List<String> expected = PUBLISHED.stream()
                .filter(RestEventCatalogueDriftTest::isConcreteEvent)
                .filter(type -> !type.isAssignableTo(UxmCancellableEvent.class))
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();

        assertThat(streamedNames())
                .describedAs("an event that exists and is not streamed is one nobody can subscribe to;"
                        + " add it to PublishedEvents, or say in that file why it is not there")
                .containsExactlyElementsOf(expected);
    }

    /** A pre-event on a socket would be a veto nobody can answer, so none of them is in the list. */
    @Test
    void noCancellableEventIsStreamed() {
        assertThat(PublishedEvents.ALL).noneMatch(UxmCancellableEvent.class::isAssignableFrom);
    }

    @Test
    void everyStreamedEventHasItsOwnName() {
        assertThat(PublishedEvents.ALL.stream().map(EventNames::of).distinct().count())
                .isEqualTo(PublishedEvents.ALL.size());
    }

    /**
     * Every field of every streamed event is a type the renderer knows.
     *
     * <p>Without this, a getter returning something new would quietly become {@code null} on the wire: an event
     * that still arrives, with the one piece of information anybody wanted missing from it.
     */
    @Test
    void everyFieldOfEveryStreamedEventCanBeRendered() {
        for (Class<? extends UxmEvent> type : PublishedEvents.ALL) {
            for (Method field : EventJson.fieldsOf(type)) {
                assertThat(EventJson.canRender(field.getReturnType()))
                        .describedAs(
                                "%s.%s returns %s, which the event renderer does not know how to write",
                                type.getSimpleName(),
                                field.getName(),
                                field.getReturnType().getSimpleName())
                        .isTrue();
            }
        }
    }

    /** The wire shape of every event, so a rename or a new field is a diff somebody agreed to. */
    @Test
    void theEventCatalogueIsWhatTheGoldenFileSays() throws IOException {
        String actual = PublishedEvents.ALL.stream()
                .map(type -> EventNames.of(type) + ": "
                        + String.join(
                                ", ",
                                EventJson.fieldsOf(type).stream()
                                        .map(EventJson::nameOf)
                                        .toList()))
                .sorted()
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElseThrow();

        Files.createDirectories(ACTUAL.getParent());
        Files.writeString(ACTUAL, actual + System.lineSeparator());

        assertThat(actual.strip())
                .describedAs("the event stream's contents changed; if that was deliberate, update %s", GOLDEN)
                .isEqualTo(Files.readString(GOLDEN).strip());
    }

    private static List<String> streamedNames() {
        return PublishedEvents.ALL.stream().map(Class::getSimpleName).sorted().toList();
    }

    private static boolean isConcreteEvent(JavaClass type) {
        return type.isAssignableTo(UxmEvent.class)
                && !type.getModifiers().contains(JavaModifier.ABSTRACT)
                && !type.getName().equals(UxmEvent.class.getName());
    }
}

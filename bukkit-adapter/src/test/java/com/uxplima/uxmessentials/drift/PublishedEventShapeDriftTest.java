package com.uxplima.uxmessentials.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bukkit.event.HandlerList;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.uxplima.uxmessentials.api.bukkit.event.UxmCancellableEvent;
import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import org.junit.jupiter.api.Test;

/**
 * The shape every published event has to keep, because getting it wrong fails on somebody else's server.
 *
 * <p>Bukkit's event contract is enforced at registration time by reflection, not by the compiler: an event without a
 * {@code public static HandlerList getHandlerList()} throws when a consumer registers a listener for it, and two
 * events that share one {@link HandlerList} deliver each other's listeners. Neither mistake is visible in review and
 * neither shows up in our own tests, since we fire these events and never listen for them. So the whole published
 * set is checked here at once.
 */
class PublishedEventShapeDriftTest {

    private static final String EVENT_PACKAGE = "com.uxplima.uxmessentials.api.bukkit.event";

    @Test
    void everyPublishedEventCanBeListenedFor() {
        List<String> broken = new ArrayList<>();
        for (Class<?> event : publishedEvents()) {
            try {
                Method handlerList = event.getMethod("getHandlerList");
                if (!Modifier.isStatic(handlerList.getModifiers())
                        || !HandlerList.class.equals(handlerList.getReturnType())) {
                    broken.add(event.getName() + " (getHandlerList is not a static HandlerList)");
                }
            } catch (NoSuchMethodException missing) {
                broken.add(event.getName() + " (no getHandlerList)");
            }
        }

        assertThat(broken)
                .as("Bukkit resolves these reflectively: without one, registering a listener throws on the "
                        + "consumer's server and there is nothing they can do about it")
                .isEmpty();
    }

    @Test
    void noTwoEventsShareAHandlerList() {
        IdentityHashMap<HandlerList, String> owners = new IdentityHashMap<>();
        List<String> shared = new ArrayList<>();
        for (Class<?> event : publishedEvents()) {
            HandlerList handlers = handlerListOf(event);
            String previous = owners.putIfAbsent(handlers, event.getName());
            if (previous != null) {
                shared.add(event.getName() + " shares its HandlerList with " + previous);
            }
        }

        assertThat(shared)
                .as("a copy-pasted HandlerList makes two events deliver each other's listeners, which looks "
                        + "like a listener firing for no reason")
                .isEmpty();
    }

    @Test
    void everyPublishedEventIsFinalAndCarriesNoMutableState() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> event : publishedEvents()) {
            if (!Modifier.isFinal(event.getModifiers())) {
                offenders.add(event.getName() + " is not final");
            }
            for (Field field : event.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!Modifier.isPrivate(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())) {
                    offenders.add(event.getName() + "." + field.getName() + " is not private final");
                }
            }
        }

        assertThat(offenders)
                .as("a published event is a fact handed to code we do not control; the only thing a listener "
                        + "may change is the cancellation flag")
                .isEmpty();
    }

    /** The naming rule the cancellables follow: a {@code Pre} that begins a word, as in {@code PreCreate}. */
    private static final Pattern PRE_STAGE = Pattern.compile("Pre[A-Z]");

    @Test
    void onlyThePreEventsAreCancellable() {
        Set<String> misnamed = new TreeSet<>();
        for (Class<?> event : publishedEvents()) {
            boolean cancellable = UxmCancellableEvent.class.isAssignableFrom(event);
            // "Pre" only counts as the prefix of a stage word, so UxmPrestigeEvent is a fact, not a veto.
            boolean namedPre = PRE_STAGE.matcher(event.getSimpleName()).find();
            if (cancellable != namedPre) {
                misnamed.add(event.getName());
            }
        }

        assertThat(misnamed)
                .as("the name is how a developer tells at a glance whether they can still stop something: "
                        + "Pre means it has not happened yet and may be cancelled, everything else is a fact")
                .isEmpty();
    }

    @Test
    void thePublishedSetIsNotEmpty() {
        // The same insurance the coverage guards carry: a scan that stops seeing the API would pass everything.
        assertThat(publishedEvents()).hasSizeGreaterThan(50);
    }

    private static HandlerList handlerListOf(Class<?> event) {
        try {
            return (HandlerList) event.getMethod("getHandlerList").invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new LinkageError("could not read the HandlerList of " + event.getName(), failure);
        }
    }

    /** Every concrete published event, which is exactly what a consumer can write a listener for. */
    private static List<Class<?>> publishedEvents() {
        JavaClasses classes = new ClassFileImporter().importPackages(EVENT_PACKAGE);
        return classes.stream()
                .filter(type -> type.isAssignableTo(UxmEvent.class) || type.isAssignableTo(UxmCancellableEvent.class))
                .filter(type -> !type.isInterface())
                .map(JavaClass::reflect)
                .filter(type -> !Modifier.isAbstract(type.getModifiers()))
                .collect(Collectors.toList());
    }
}

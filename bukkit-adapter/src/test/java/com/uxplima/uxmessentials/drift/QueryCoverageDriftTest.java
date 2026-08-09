package com.uxplima.uxmessentials.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import org.junit.jupiter.api.Test;

/**
 * A published query nobody can reach is not a published query.
 *
 * <p>Three things have to line up for a consumer to get an answer: the interface exists, the front door hands it
 * out, and exactly one context fills it in at wiring time. Each is written in a different file, and the compiler
 * checks none of the three against the others: an interface with no accessor compiles, an accessor over a surface
 * nothing registers compiles and answers empty forever, and a second registration of the same type only fails once
 * a server starts with both modules on. So the three lists are compared here.
 *
 * <p>Empty is the honest answer for a module the operator switched off, which is exactly why a missing registration
 * is so quiet: it is indistinguishable from a disabled module until somebody checks their config and finds it on.
 */
class QueryCoverageDriftTest {

    private static final String QUERY_PACKAGE = "com.uxplima.uxmessentials.api.query";

    /** Where a context's implementation of its published query belongs, by convention and by every existing one. */
    private static final String IMPLEMENTATION_SUFFIX = ".adapter.outbound.api";

    @Test
    void everyPublishedQueryIsHandedOutByTheFrontDoor() {
        Set<String> reachable = frontDoorSurfaces();

        assertThat(publishedQueries())
                .as("these query interfaces exist but no UxmEssentialsApi accessor returns them, so a consumer "
                        + "has no way to reach one: add an Optional<Xxx> accessor to the front door")
                .allSatisfy(query -> assertThat(reachable).contains(query.getName()));
    }

    @Test
    void everyFrontDoorAccessorReturnsAQueryThatExists() {
        Set<String> declared =
                publishedQueries().stream().map(Class::getName).collect(Collectors.toCollection(TreeSet::new));

        assertThat(frontDoorSurfaces())
                .as("the front door hands out something that is not a published query interface")
                .isSubsetOf(declared);
    }

    @Test
    void everyPublishedQueryIsFilledInByExactlyOneContext() {
        String bootstrap = pluginModuleSource();
        TreeMap<String, Integer> registrations = new TreeMap<>();
        for (Class<?> query : publishedQueries()) {
            registrations.put(query.getSimpleName(), count(bootstrap, query.getSimpleName() + ".class"));
        }

        assertThat(registrations)
                .as("each published query must be registered exactly once in PluginModule: zero means a consumer "
                        + "gets empty on a server where the module is on, and two means two contexts claim it")
                .allSatisfy((query, times) -> assertThat(times)
                        .withFailMessage("%s is registered %d times in PluginModule, expected once", query, times)
                        .isEqualTo(1));
    }

    @Test
    void everyPublishedQueryHasExactlyOneImplementation() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.uxplima.uxmessentials");

        for (Class<?> query : publishedQueries()) {
            java.util.List<String> implementations = production.stream()
                    .filter(type -> type.isAssignableTo(query))
                    .filter(type -> !type.isInterface())
                    .map(JavaClass::getName)
                    .sorted()
                    .toList();

            assertThat(implementations)
                    .as("%s should have one implementation, in its own context", query.getSimpleName())
                    .hasSize(1);
            assertThat(implementations.getFirst())
                    .as("a published query is implemented by the context that owns the state, in its outbound "
                            + "adapter package, so the read runs over the same ports the commands do")
                    .contains(IMPLEMENTATION_SUFFIX);
        }
    }

    @Test
    void thereIsSomethingToCheck() {
        // A scan that stopped finding the query package would otherwise agree with itself about nothing.
        assertThat(publishedQueries()).hasSizeGreaterThan(10);
    }

    /** Every {@code Uxm...Query} interface the API module publishes. */
    private static Set<Class<?>> publishedQueries() {
        return new ClassFileImporter()
                .importPackages(QUERY_PACKAGE).stream()
                        .map(JavaClass::reflect)
                        .filter(Class::isInterface)
                        .filter(type -> Modifier.isPublic(type.getModifiers()))
                        .filter(type -> type.getSimpleName().endsWith("Query"))
                        .collect(Collectors.toCollection(
                                () -> new TreeSet<>(java.util.Comparator.comparing(Class::getName))));
    }

    /** The surfaces the front door hands out, read from the {@code Optional<T>} each accessor returns. */
    private static Set<String> frontDoorSurfaces() {
        return Arrays.stream(UxmEssentialsApi.class.getDeclaredMethods())
                .filter(method -> Optional.class.equals(method.getReturnType()))
                .map(QueryCoverageDriftTest::elementType)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Optional<String> elementType(Method accessor) {
        if (accessor.getGenericReturnType() instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length == 1 && arguments[0] instanceof Class<?> element) {
                return Optional.of(element.getName());
            }
        }
        return Optional.empty();
    }

    private static int count(String haystack, String needle) {
        int found = 0;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return found;
            }
            found++;
            from = at + needle.length();
        }
    }

    private static String pluginModuleSource() {
        Path file = repoRoot()
                .resolve("bukkit-adapter/src/main/java/com/uxplima/uxmessentials/bootstrap/di/PluginModule.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + file, failure);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}

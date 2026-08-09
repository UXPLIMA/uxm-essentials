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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.uxplima.uxmessentials.api.action.UxmActions;
import org.junit.jupiter.api.Test;

/**
 * The same three-way check {@code QueryCoverageDriftTest} makes for reads, made for writes.
 *
 * <p>A write surface can go missing more quietly than a read one. An unregistered query answers empty and somebody
 * eventually asks why; an unregistered action is simply never called, and the plugin that would have called it
 * decides at startup that the module is off and takes its own path. Nothing ever reports it.
 */
class ActionCoverageDriftTest {

    private static final String ACTION_PACKAGE = "com.uxplima.uxmessentials.api.action";

    /** Where a context's implementation of its published actions belongs, as with the queries. */
    private static final String IMPLEMENTATION_SUFFIX = ".adapter.outbound.api";

    @Test
    void everyPublishedActionSurfaceIsHandedOut() {
        Set<String> reachable = bundleSurfaces();

        assertThat(publishedActions())
                .as("these action interfaces exist but no UxmActions accessor returns them, so a consumer has no "
                        + "way to reach one: add an Optional<Xxx> accessor to the bundle")
                .allSatisfy(actions -> assertThat(reachable).contains(actions.getName()));
    }

    @Test
    void everyBundleAccessorReturnsASurfaceThatExists() {
        Set<String> declared =
                publishedActions().stream().map(Class::getName).collect(Collectors.toCollection(TreeSet::new));

        assertThat(bundleSurfaces())
                .as("the bundle hands out something that is not a published action interface")
                .isSubsetOf(declared);
    }

    @Test
    void everyPublishedActionSurfaceIsFilledInByExactlyOneContext() {
        String bootstrap = pluginModuleSource();
        TreeMap<String, Integer> registrations = new TreeMap<>();
        for (Class<?> actions : publishedActions()) {
            registrations.put(actions.getSimpleName(), count(bootstrap, actions.getSimpleName() + ".class"));
        }

        assertThat(registrations)
                .as("each published action surface must be registered exactly once in PluginModule: zero means a "
                        + "consumer silently decides the module is off, and two means two contexts claim it")
                .allSatisfy((surface, times) -> assertThat(times)
                        .withFailMessage("%s is registered %d times in PluginModule, expected once", surface, times)
                        .isEqualTo(1));
    }

    @Test
    void everyPublishedActionSurfaceHasExactlyOneImplementation() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.uxplima.uxmessentials");

        for (Class<?> actions : publishedActions()) {
            List<String> implementations = production.stream()
                    .filter(type -> type.isAssignableTo(actions))
                    .filter(type -> !type.isInterface())
                    .map(JavaClass::getName)
                    .sorted()
                    .toList();

            assertThat(implementations)
                    .as("%s should have one implementation, in its own context", actions.getSimpleName())
                    .hasSize(1);
            assertThat(implementations.getFirst())
                    .as("a published action is implemented by the context that owns the state, in its outbound "
                            + "adapter package, so the write runs the same use case the command does")
                    .contains(IMPLEMENTATION_SUFFIX);
        }
    }

    @Test
    void everyActionAnswersRatherThanBlocks() {
        for (Class<?> actions : publishedActions()) {
            for (Method method : actions.getDeclaredMethods()) {
                if (method.getReturnType().equals(actions)) {
                    // A method answering with its own surface picks a variation of it rather than writing
                    // anything: nothing has reached the database by the time it returns, so there is nothing to
                    // wait for. Every verb reached through it is still held to the rule below.
                    continue;
                }
                assertThat(method.getReturnType())
                        .as(
                                "%s.%s must return a CompletableFuture: a write reaches the database, and a consumer "
                                        + "calling it from a listener would otherwise stall the tick thread",
                                actions.getSimpleName(), method.getName())
                        .isEqualTo(java.util.concurrent.CompletableFuture.class);
            }
        }
    }

    @Test
    void thereIsSomethingToCheck() {
        assertThat(publishedActions()).isNotEmpty();
    }

    /** Every {@code Uxm...Actions} interface the API module publishes, except the bundle that hands them out. */
    private static Set<Class<?>> publishedActions() {
        return new ClassFileImporter()
                .importPackages(ACTION_PACKAGE).stream()
                        .map(JavaClass::reflect)
                        .filter(Class::isInterface)
                        .filter(type -> Modifier.isPublic(type.getModifiers()))
                        .filter(type -> type.getSimpleName().endsWith("Actions"))
                        .filter(type -> !type.equals(UxmActions.class))
                        .collect(Collectors.toCollection(
                                () -> new TreeSet<>(java.util.Comparator.comparing(Class::getName))));
    }

    /** The surfaces the bundle hands out, read from the {@code Optional<T>} each accessor returns. */
    private static Set<String> bundleSurfaces() {
        return Arrays.stream(UxmActions.class.getDeclaredMethods())
                .filter(method -> Optional.class.equals(method.getReturnType()))
                .map(ActionCoverageDriftTest::elementType)
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

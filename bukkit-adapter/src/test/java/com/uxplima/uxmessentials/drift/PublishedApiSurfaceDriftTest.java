package com.uxplima.uxmessentials.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * The published API surface, written down so that changing it is a decision somebody makes on purpose.
 *
 * <p>Third-party plugins compile against these two artifacts and are not rebuilt when the server updates. Renaming a
 * method or dropping a constructor therefore does not break our build, it breaks theirs, months later, as a
 * {@code NoSuchMethodError} on somebody's server. Nothing in the compiler notices, because from our side the change
 * looks local.
 *
 * <p>So the whole surface is dumped to {@code api-surface.txt} and compared. Adding to it is ordinary and the diff
 * is short; removing or renaming shows up as a deletion, which is the review moment this exists to create. When this
 * fails it writes what it actually saw to {@code build/api-surface.actual.txt}: once the change is deliberate, copy
 * that over the golden file and let the diff be reviewed.
 */
class PublishedApiSurfaceDriftTest {

    /** The published root: :api and :bukkit-api both live under it, and nothing else does. */
    private static final String PUBLISHED_ROOT = "com.uxplima.uxmessentials.api";

    private static final Path GOLDEN = Path.of("src", "test", "resources", "api-surface.txt");
    private static final Path ACTUAL = Path.of("build", "api-surface.actual.txt");

    @Test
    void thePublishedSurfaceIsWhatWeSaidItWas() {
        List<String> current = surface();
        write(ACTUAL, current);

        assertThat(current)
                .as("the published API surface changed. A line that disappeared is a break for every plugin "
                        + "already compiled against it; if the change is deliberate, copy build/"
                        + "api-surface.actual.txt over src/test/resources/api-surface.txt and let the diff be "
                        + "reviewed")
                .isEqualTo(golden());
    }

    @Test
    void theSurfaceIsNotEmpty() {
        // A scan that stopped seeing the API would otherwise agree with an empty golden file.
        assertThat(surface()).hasSizeGreaterThan(200);
    }

    private static List<String> golden() {
        try {
            return Files.readAllLines(GOLDEN, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException("could not read " + GOLDEN.toAbsolutePath(), failure);
        }
    }

    private static void write(Path target, List<String> lines) {
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.writeString(target, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("could not write " + target.toAbsolutePath(), failure);
        }
    }

    /** Every public member a consumer can bind to, one per line, in a stable order. */
    private static List<String> surface() {
        JavaClasses classes = new ClassFileImporter().importPackages(PUBLISHED_ROOT);
        return classes.stream()
                .map(JavaClass::reflect)
                .filter(type -> !type.isAnonymousClass() && !type.isLocalClass() && !type.isSynthetic())
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .sorted(Comparator.comparing(Class::getName))
                .flatMap(type -> membersOf(type).stream())
                .toList();
    }

    private static List<String> membersOf(Class<?> type) {
        List<String> lines = new ArrayList<>();
        lines.add(kindOf(type) + " " + type.getName() + extendsClause(type));
        Arrays.stream(type.getDeclaredConstructors())
                .filter(member -> isPublished(member.getModifiers()))
                .map(PublishedApiSurfaceDriftTest::signatureOf)
                .sorted()
                .forEach(line -> lines.add("  " + line));
        Arrays.stream(type.getDeclaredMethods())
                .filter(member -> isPublished(member.getModifiers()))
                .filter(member -> !member.isSynthetic())
                .map(PublishedApiSurfaceDriftTest::signatureOf)
                .sorted()
                .forEach(line -> lines.add("  " + line));
        Arrays.stream(type.getDeclaredFields())
                .filter(field -> isPublished(field.getModifiers()))
                .map(field -> simple(field.getType()) + " " + field.getName())
                .sorted()
                .forEach(line -> lines.add("  " + line));
        return lines;
    }

    /** Protected counts: a consumer subclasses our event bases, so those members are part of the promise too. */
    private static boolean isPublished(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String kindOf(Class<?> type) {
        if (type.isInterface()) {
            return "interface";
        }
        if (type.isEnum()) {
            return "enum";
        }
        if (type.isRecord()) {
            return "record";
        }
        return Modifier.isAbstract(type.getModifiers()) ? "abstract class" : "class";
    }

    private static String extendsClause(Class<?> type) {
        Class<?> parent = type.getSuperclass();
        return parent == null || Object.class.equals(parent) || Enum.class.equals(parent) || Record.class.equals(parent)
                ? ""
                : " extends " + parent.getName();
    }

    private static String signatureOf(Executable member) {
        String name = member instanceof Constructor<?> ? "<init>" : member.getName();
        String returns = member instanceof Method method ? simple(method.getReturnType()) + " " : "";
        String parameters = Arrays.stream(member.getParameters())
                .map(Parameter::getType)
                .map(PublishedApiSurfaceDriftTest::simple)
                .collect(Collectors.joining(", "));
        return returns + name + "(" + parameters + ")";
    }

    /** Our own types keep their package (that is what a consumer imports); JDK and Bukkit types are shortened. */
    private static String simple(Class<?> type) {
        if (type.isArray()) {
            return simple(type.getComponentType()) + "[]";
        }
        return type.getName().startsWith("com.uxplima.") ? type.getName() : type.getSimpleName();
    }
}

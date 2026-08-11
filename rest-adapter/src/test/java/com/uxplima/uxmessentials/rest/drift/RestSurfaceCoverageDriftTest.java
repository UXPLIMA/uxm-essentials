package com.uxplima.uxmessentials.rest.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Every verb the plugin publishes is reachable over HTTP, or is written down here as one that deliberately is not.
 *
 * <p>The route table guard says the routes have not changed. It cannot say the routes are enough. A method added to
 * a published surface and never given a route is a gap nothing reports: the add-on still builds, every existing
 * route still answers, and the only symptom is a panel author eventually asking why the thing they can do from Java
 * cannot be done over HTTP. That question is what this test asks first.
 *
 * <p>What counts as covered is that the route sources name the method, by call or by reference. A loose check on
 * purpose: it is looking for a gap in the surface, not auditing what each route does with what it calls, and the
 * route tests are where behaviour is pinned.
 *
 * <p>When a verb genuinely should not have a route, add it to {@link #DELIBERATELY_ABSENT} with the reason. The
 * list is short and every line of it is an argument, which is the point: not having a route is a decision somebody
 * made rather than one nobody noticed.
 */
class RestSurfaceCoverageDriftTest {

    private static final String QUERY_PACKAGE = "com.uxplima.uxmessentials.api.query";
    private static final String ACTION_PACKAGE = "com.uxplima.uxmessentials.api.action";

    private static final Path ROUTES = Path.of("src", "main", "java", "com", "uxplima", "uxmessentials", "rest");

    /**
     * The verbs with no route of their own, and why. Each is a shorthand whose answer the routes already carry, so
     * a caller loses nothing: the fuller read is one request, and the shorthand would be a second path answering a
     * field of the first.
     */
    private static final Set<String> DELIBERATELY_ABSENT = Set.of(
            // Every one of these is a boolean already present in the payload of the read beside it.
            "UxmCommandControlQuery.isBlocked", // command-check carries "allowed"
            "UxmDiscordLinkQuery.isLinked", // the link read is the link or null
            "UxmPresenceQuery.isAfk", // the presence read carries "afk"
            "UxmSecurityQuery.isLockedOut", // the security read carries the lockout window
            "UxmTradeQuery.isTrading", // the trade read is the session or null
            "UxmWorldsQuery.isLoaded", // each world in the list carries "loaded"
            // And these are list shorthands whose list the routes already return in full.
            "UxmKitsQuery.claimableBy", // the per-player kit list carries "can-claim" on every kit
            "UxmMessagingQuery.ignores"); // the ignore list is returned whole, so a pair is a lookup in it

    @Test
    void everyPublishedVerbIsReachableOverHttpOrWrittenDownAsNotBeing() {
        String routes = routeSources();

        Set<String> uncovered = published()
                .filter(verb -> !DELIBERATELY_ABSENT.contains(verb.name()))
                .filter(verb -> !verb.appearsIn(routes))
                .map(Verb::name)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(uncovered)
                .describedAs("these published verbs have no route: give each one a route, or add it to "
                        + "DELIBERATELY_ABSENT with the reason it should not have one")
                .isEmpty();
    }

    /** A verb that gained a route stops being an exemption, and the reason beside it stops being true. */
    @Test
    void nothingIsWrittenDownAsAbsentWhileHavingARoute() {
        String routes = routeSources();

        Set<String> covered = published()
                .filter(verb -> DELIBERATELY_ABSENT.contains(verb.name()))
                .filter(verb -> verb.appearsIn(routes))
                .map(Verb::name)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(covered)
                .describedAs("these are listed as deliberately absent but the routes call them; remove them from "
                        + "DELIBERATELY_ABSENT so the list keeps meaning what it says")
                .isEmpty();
    }

    /** An exemption for a verb that no longer exists is a comment nobody will ever check again. */
    @Test
    void nothingIsWrittenDownAsAbsentThatNoLongerExists() {
        Set<String> exists = published().map(Verb::name).collect(Collectors.toCollection(TreeSet::new));

        assertThat(DELIBERATELY_ABSENT)
                .describedAs("DELIBERATELY_ABSENT names a verb the API no longer has")
                .isSubsetOf(exists);
    }

    @Test
    void thereIsSomethingToCheck() {
        assertThat(published().count()).isGreaterThan(100);
    }

    /** Every method of every published query and action surface, as {@code Interface.method}. */
    private static Stream<Verb> published() {
        return Stream.of(QUERY_PACKAGE, ACTION_PACKAGE)
                .flatMap(pkg -> new ClassFileImporter().importPackages(pkg).stream())
                .map(JavaClass::reflect)
                .filter(Class::isInterface)
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .filter(RestSurfaceCoverageDriftTest::isSurface)
                .flatMap(type -> Stream.of(type.getDeclaredMethods())
                        .map(Method::getName)
                        .distinct()
                        .map(method -> new Verb(type.getSimpleName(), method)))
                .sorted(Comparator.comparing(Verb::name))
                .distinct();
    }

    /**
     * A published surface is one a consumer reaches things through. The two bundles that hand the surfaces out are
     * not themselves surfaces, and their accessors are covered by the module checks each route already makes.
     */
    private static boolean isSurface(Class<?> type) {
        String name = type.getSimpleName();
        return !name.equals("UxmActions") && (name.endsWith("Query") || name.endsWith("Actions"));
    }

    /** Every route source, read as text, since what is being asked is whether a name appears at all. */
    private static String routeSources() {
        try (Stream<Path> files = Files.walk(ROUTES)) {
            List<Path> java =
                    files.filter(path -> path.toString().endsWith(".java")).toList();
            StringBuilder all = new StringBuilder();
            for (Path file : java) {
                all.append(Files.readString(file, StandardCharsets.UTF_8));
            }
            return all.toString();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("failed to read the route sources under " + ROUTES, unreadable);
        }
    }

    /** One published verb: which surface it is on, and what it is called. */
    private record Verb(String surface, String method) {

        String name() {
            return surface + "." + method;
        }

        /** Called or referenced: {@code .thing(} or {@code ::thing}, which is how a route reaches one. */
        boolean appearsIn(String sources) {
            return Pattern.compile("[.:]" + Pattern.quote(method) + "\\s*\\(")
                            .matcher(sources)
                            .find()
                    || Pattern.compile("::" + Pattern.quote(method) + "\\b")
                            .matcher(sources)
                            .find();
        }
    }
}

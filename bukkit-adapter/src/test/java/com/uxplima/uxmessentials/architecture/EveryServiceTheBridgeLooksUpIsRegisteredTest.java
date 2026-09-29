package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Discord bridge and the host find each other through Bukkit's {@code ServicesManager}, so both sides must name
 * the same class, and one side must register what the other looks up.
 *
 * <p>Both halves failed on a customer's server. The bridge looked up a notification source that it declared itself
 * and nothing registered, so audit lines never reached Discord. It also carried its own copy of {@code :api}, so the
 * link confirmation the host did register was a different class to it and {@code /link} never worked. A service
 * shared by the two jars is declared in {@code :api}, the one module the bridge takes from the host at run time,
 * and a lookup with nobody registering it is a feature that cannot work.
 */
final class EveryServiceTheBridgeLooksUpIsRegisteredTest {

    private static final Pattern LOOKUP = Pattern.compile("getRegistration\\(\\s*(\\w+)\\.class\\s*\\)");
    private static final Pattern REGISTRATION = Pattern.compile("\\.register\\(\\s*(\\w+)\\.class\\s*,");

    @Test
    @DisplayName("each service one side looks up is an :api type the other side registers")
    void everyLookupIsASharedTypeTheOtherSideRegisters() {
        Set<String> shared = apiTypes();
        Set<String> bridgeLooksUp = matches("discord-adapter", LOOKUP);
        Set<String> hostRegisters = matches("bukkit-adapter", REGISTRATION);
        Set<String> hostLooksUp = matches("bukkit-adapter", LOOKUP);
        Set<String> bridgeRegisters = matches("discord-adapter", REGISTRATION);
        hostLooksUp.retainAll(bridgeTypes());

        assertThat(bridgeLooksUp)
                .describedAs("the bridge finds nothing, and it says so on every start")
                .isNotEmpty();
        assertThat(bridgeLooksUp).describedAs("declare these in :api").isSubsetOf(shared);
        assertThat(bridgeLooksUp).describedAs("register these in the host").isSubsetOf(hostRegisters);
        assertThat(hostLooksUp).describedAs("register these in the bridge").isSubsetOf(bridgeRegisters);
    }

    /** The simple names of the types the bridge declares or shares with the host: what the host may ask it for. */
    private static Set<String> bridgeTypes() {
        Set<String> names = apiTypes();
        for (Path file : ProductionSources.files()) {
            if (file.toString().contains("discord-adapter")) {
                names.add(simpleName(file));
            }
        }
        return names;
    }

    private static Set<String> apiTypes() {
        Set<String> names = new TreeSet<>();
        for (Path file : ProductionSources.files()) {
            if (file.toString().contains("/api/src/main/java/") && !file.endsWith("package-info.java")) {
                names.add(simpleName(file));
            }
        }
        return names;
    }

    private static Set<String> matches(String module, Pattern pattern) {
        Set<String> names = new TreeSet<>();
        for (Path file : ProductionSources.files()) {
            if (!file.toString().contains("/" + module + "/")) {
                continue;
            }
            Matcher matcher = pattern.matcher(ProductionSources.code(ProductionSources.read(file)));
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static String simpleName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }
}

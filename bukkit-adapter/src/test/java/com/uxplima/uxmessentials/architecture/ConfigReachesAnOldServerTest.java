package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The promise the shipped configuration makes to a server that was installed a year ago.
 *
 * <p>A plugin that writes its files on the first run and never again gives every later key to a new server
 * and to no existing one: the operator who has run it longest is the one who never gets the knob, and they
 * find out by reading a changelog. This plugin closes that in {@code DefaultResources.writeInto}, which
 * appends what an update added against the baseline under {@code .defaults/}, and
 * {@code BundledDefaultsMergeTest} and {@code DefaultResourcesTest} prove what it does with a file.
 *
 * <p>What neither of those can see is the boot. The merge is one call in the plugin's {@code onEnable}, and
 * a refactor that drops it leaves every test green and every operator on the keys they installed with. That
 * is what this guard reads. Every plugin of this family has one, under this name: the others call
 * {@code PluginSettings.bringUpToDate}, and this one calls the richer thing it already had.
 */
final class ConfigReachesAnOldServerTest {

    @Test
    @DisplayName("the boot writes the shipped defaults and appends what an update added")
    void theBootKeepsTheOperatorsFilesCurrent() {
        List<Path> callers = new ArrayList<>();
        for (Path source : ProductionSources.files()) {
            if (!source.toString().contains("bootstrap")) {
                continue;
            }
            if (ProductionSources.code(ProductionSources.read(source)).contains("DefaultResources.writeInto(")) {
                callers.add(source);
            }
        }

        assertThat(callers)
                .describedAs("a boot that never calls DefaultResources.writeInto gives the keys an update"
                        + " adds to new servers only")
                .isNotEmpty();
    }
}

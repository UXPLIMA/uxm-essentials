package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The console banner stays a closed box whatever the version reads. Its version line was padded by a fixed
 * count that fitted {@code 0.10.0} only, so {@code 0.10.1-dev.1} pushed the right edge out of the frame.
 */
class StartupBannerTest {

    @Test
    void everyLineIsAsWideAsTheFrame() {
        for (String version : new String[] {"0.10.0", "0.10.1-dev.1", "0.10.1-dev.1-d5752d3"}) {
            assertThat(StartupBanner.lines(version))
                    .as("banner for %s", version)
                    .allSatisfy(line -> assertThat(line).hasSize(StartupBanner.WIDTH))
                    .anySatisfy(line -> assertThat(line).contains("Essentials v" + version));
        }
    }
}

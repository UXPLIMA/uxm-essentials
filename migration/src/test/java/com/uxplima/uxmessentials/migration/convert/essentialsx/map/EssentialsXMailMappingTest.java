package com.uxplima.uxmessentials.migration.convert.essentialsx.map;

import static com.uxplima.uxmessentials.migration.EssentialsXFixture.ALEX;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import com.uxplima.uxmessentials.migration.EssentialsXFixture;
import com.uxplima.uxmessentials.migration.convert.map.ImportedMail;
import org.junit.jupiter.api.Test;

class EssentialsXMailMappingTest {

    @Test
    void everyMailFormatEssentialsXWritesMapsToItsSenderTextTimeAndReadFlag() {
        assertThat(EssentialsXFixture.steve().mail())
                .containsExactly(
                        new ImportedMail(
                                Optional.of(ALEX),
                                "Alex",
                                "Meet me at <spawn> \\o/",
                                Optional.of(Instant.ofEpochMilli(1_700_000_000_000L)),
                                Optional.empty(),
                                true),
                        new ImportedMail(
                                Optional.empty(),
                                "Console",
                                "Server restarts tonight",
                                Optional.of(Instant.ofEpochMilli(1_700_000_600_000L)),
                                Optional.empty(),
                                false),
                        new ImportedMail(
                                Optional.empty(), "Notch", "old style mail", Optional.empty(), Optional.empty(), false),
                        new ImportedMail(
                                Optional.empty(),
                                "Jeb_",
                                "plain string mail",
                                Optional.empty(),
                                Optional.empty(),
                                false),
                        new ImportedMail(
                                Optional.empty(),
                                "EssentialsX",
                                "just words without a sender",
                                Optional.empty(),
                                Optional.empty(),
                                false),
                        new ImportedMail(
                                Optional.of(ALEX),
                                "Alex",
                                "long gone",
                                Optional.of(Instant.ofEpochMilli(1_600_000_000_000L)),
                                Optional.of(Instant.ofEpochMilli(1_600_000_060_000L)),
                                false));
    }
}

package com.uxplima.uxmessentials.bootstrap.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import org.junit.jupiter.api.Test;

/** The doctor's placeholders line tells apart both forms, only the older one, none taken, and none to take. */
class PlaceholderExpansionHealthCheckTest {

    @Test
    void bothFormsAnsweringIsHealthy() {
        HealthResult result =
                new PlaceholderExpansionHealthCheck(() -> true, () -> true, () -> true, () -> true).check();

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
    }

    @Test
    void onlyTheOlderFormAnsweringWarnsThatUxmIsTaken() {
        HealthResult result =
                new PlaceholderExpansionHealthCheck(() -> true, () -> false, () -> true, () -> true).check();

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("%uxmessentials_<key>%", "uxm identifier");
    }

    @Test
    void nothingPublishedWithPlaceholderApiPresentWarnsAboutTheGroups() {
        HealthResult result =
                new PlaceholderExpansionHealthCheck(() -> false, () -> false, () -> false, () -> true).check();

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("no placeholder group");
    }

    @Test
    void groupsEnabledButNothingHeldWarnsThatTheIdentifiersWereTaken() {
        HealthResult result =
                new PlaceholderExpansionHealthCheck(() -> false, () -> false, () -> true, () -> true).check();

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message())
                .contains("not held", "papi_expansion_evicted")
                .doesNotContain("no placeholder group");
    }
}

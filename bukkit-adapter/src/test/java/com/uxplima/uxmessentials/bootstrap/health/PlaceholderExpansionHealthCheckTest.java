package com.uxplima.uxmessentials.bootstrap.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import org.junit.jupiter.api.Test;

/** The doctor's placeholders line tells apart the current form answering, only the older one, and nothing. */
class PlaceholderExpansionHealthCheckTest {

    @Test
    void bothFormsAnsweringIsHealthy() {
        HealthResult result = new PlaceholderExpansionHealthCheck(() -> true, () -> true, () -> true).check();

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
    }

    @Test
    void onlyTheOlderFormAnsweringWarnsThatUxmIsTaken() {
        HealthResult result = new PlaceholderExpansionHealthCheck(() -> true, () -> false, () -> true).check();

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("%uxmessentials_<key>%", "uxm identifier");
    }

    @Test
    void nothingPublishedWithPlaceholderApiPresentWarnsAboutTheGroups() {
        HealthResult result = new PlaceholderExpansionHealthCheck(() -> false, () -> false, () -> true).check();

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("no placeholder group");
    }
}

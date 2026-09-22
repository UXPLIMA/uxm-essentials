package com.uxplima.uxmessentials.bootstrap.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The placeholders line of {@code /uxmess doctor}.
 *
 * <p>Three states and they are not the same sentence. Published is the ordinary one. No PlaceholderAPI is the
 * operator's own choice and costs them only the placeholders, so it warns and says the rest still works.
 * PlaceholderAPI present and nothing published is the one worth reading twice: the operator installed the
 * plugin for our placeholders and no group is enabled, so every one of them reads as empty in their scoreboard.
 */
class PlaceholderExpansionHealthCheckTest {

    @Test
    @DisplayName("published is the ordinary answer")
    void published() {
        PlaceholderExpansionHealthCheck check = new PlaceholderExpansionHealthCheck(true, () -> true);

        assertThat(check.check().status()).isEqualTo(HealthStatus.OK);
        assertThat(check.check().message()).contains("published");
    }

    @Test
    @DisplayName("no PlaceholderAPI warns and says the rest of the plugin is unaffected")
    void noPlaceholderApi() {
        PlaceholderExpansionHealthCheck check = new PlaceholderExpansionHealthCheck(false, () -> false);

        assertThat(check.check().status()).isEqualTo(HealthStatus.WARN);
        assertThat(check.check().message()).contains("PlaceholderAPI");
    }

    @Test
    @DisplayName("PlaceholderAPI with nothing published names the reason, which is ours")
    void nothingPublished() {
        PlaceholderExpansionHealthCheck check = new PlaceholderExpansionHealthCheck(false, () -> true);

        assertThat(check.check().status()).isEqualTo(HealthStatus.WARN);
        assertThat(check.check().message())
                .describedAs("the operator installed PlaceholderAPI for these, so say why they got none")
                .contains("no placeholder group");
    }

    @Test
    @DisplayName("the name is the one the estate rule writes")
    void named() {
        assertThat(new PlaceholderExpansionHealthCheck(true, () -> true).name()).isEqualTo("placeholders");
    }
}

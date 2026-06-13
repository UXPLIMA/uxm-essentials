package com.uxplima.uxmessentials.vote.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import org.junit.jupiter.api.Test;

/**
 * Parses {@code discord} config blocks into {@link VoteDiscordSettings}: the derived {@code enabled} flag
 * (true only when a webhook URL is set), the documented defaults when keys are absent, the tolerant period
 * parse (unknown -> MONTHLY), and the clamping of a sloppy limit / interval to at least one.
 */
class VoteDiscordSettingsTest {

    @Test
    void anEmptyWebhookUrlDisablesDiscordButStillCarriesDefaults() {
        VoteDiscordSettings settings = VoteDiscordSettings.fromConfig(new MapConfigStore(Map.of()));

        assertThat(settings.enabled()).isFalse();
        assertThat(settings.webhookUrl()).isEmpty();
        assertThat(settings.voteEnabled()).isTrue();
        assertThat(settings.voteTemplate()).isEqualTo("{player} just voted on {service}!");
        assertThat(settings.partyEnabled()).isTrue();
        assertThat(settings.partyTemplate()).isEqualTo("The vote party fired at {threshold} votes!");
        assertThat(settings.topVoter().enabled()).isFalse();
        assertThat(settings.topVoter().period()).isEqualTo(VotePeriod.MONTHLY);
        assertThat(settings.topVoter().limit()).isEqualTo(10);
        assertThat(settings.topVoter().intervalMinutes()).isEqualTo(1440L);
        assertThat(settings.topVoter().title()).isEqualTo("Top Voters");
    }

    @Test
    void aWebhookUrlEnablesDiscordOutput() {
        MapConfigStore config =
                new MapConfigStore(Map.of("discord.webhook-url", "https://discord.com/api/webhooks/1/abc"));

        VoteDiscordSettings settings = VoteDiscordSettings.fromConfig(config);

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.webhookUrl()).isEqualTo("https://discord.com/api/webhooks/1/abc");
    }

    @Test
    void blankUrlWithSurroundingWhitespaceIsTreatedAsDisabled() {
        VoteDiscordSettings settings =
                VoteDiscordSettings.fromConfig(new MapConfigStore(Map.of("discord.webhook-url", "   ")));

        assertThat(settings.enabled()).isFalse();
    }

    @Test
    void everyFieldIsReadFromConfig() {
        Map<String, Object> values = new HashMap<>();
        values.put("discord.webhook-url", "https://discord.com/api/webhooks/2/xyz");
        values.put("discord.vote.enabled", false);
        values.put("discord.vote.template", "vote {player}");
        values.put("discord.party.enabled", false);
        values.put("discord.party.template", "party {threshold}");
        values.put("discord.top-voter.enabled", true);
        values.put("discord.top-voter.period", "weekly");
        values.put("discord.top-voter.limit", 5);
        values.put("discord.top-voter.interval-minutes", 60L);
        values.put("discord.top-voter.title", "Weekly Champs");

        VoteDiscordSettings settings = VoteDiscordSettings.fromConfig(new MapConfigStore(values));

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.voteEnabled()).isFalse();
        assertThat(settings.voteTemplate()).isEqualTo("vote {player}");
        assertThat(settings.partyEnabled()).isFalse();
        assertThat(settings.partyTemplate()).isEqualTo("party {threshold}");
        assertThat(settings.topVoter().enabled()).isTrue();
        // The period parse is case-insensitive.
        assertThat(settings.topVoter().period()).isEqualTo(VotePeriod.WEEKLY);
        assertThat(settings.topVoter().limit()).isEqualTo(5);
        assertThat(settings.topVoter().intervalMinutes()).isEqualTo(60L);
        assertThat(settings.topVoter().title()).isEqualTo("Weekly Champs");
    }

    @Test
    void anUnknownPeriodFallsBackToMonthly() {
        VoteDiscordSettings settings =
                VoteDiscordSettings.fromConfig(new MapConfigStore(Map.of("discord.top-voter.period", "NONSENSE")));

        assertThat(settings.topVoter().period()).isEqualTo(VotePeriod.MONTHLY);
    }

    @Test
    void aSloppyLimitAndIntervalAreClampedToAtLeastOne() {
        Map<String, Object> values = new HashMap<>();
        values.put("discord.top-voter.limit", 0);
        values.put("discord.top-voter.interval-minutes", 0L);

        VoteDiscordSettings settings = VoteDiscordSettings.fromConfig(new MapConfigStore(values));

        assertThat(settings.topVoter().limit()).isEqualTo(1);
        assertThat(settings.topVoter().intervalMinutes()).isEqualTo(1L);
    }

    /** A flat-map {@link ConfigStore} that returns the typed value at a dotted path, or the fallback. */
    private static final class MapConfigStore implements ConfigStore {
        private final Map<String, Object> values;

        MapConfigStore(Map<String, Object> values) {
            this.values = Map.copyOf(values);
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            Object value = values.get(path);
            return value instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            Object value = values.get(path);
            return value instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            Object value = values.get(path);
            return value instanceof Integer i ? i : fallback;
        }

        @Override
        public long getLong(String path, long fallback) {
            Object value = values.get(path);
            if (value instanceof Long l) {
                return l;
            }
            return value instanceof Integer i ? i.longValue() : fallback;
        }
    }
}

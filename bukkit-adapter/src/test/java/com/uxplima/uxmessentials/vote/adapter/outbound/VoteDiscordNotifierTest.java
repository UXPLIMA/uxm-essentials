package com.uxplima.uxmessentials.vote.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.adapter.outbound.VoteDiscordSettings.TopVoter;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.event.VoteReceived;
import com.uxplima.uxmlib.discord.DiscordEmbed;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link VoteDiscordNotifier} without any HTTP: the pure embed builders (per-vote, per-party, and the
 * top-voter leaderboard) produce the expected title / description / fields, a malformed webhook URL leaves
 * the notifier inactive without throwing, and an off toggle or an unrelated event is ignored.
 */
class VoteDiscordNotifierTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    @Test
    void buildVoteEmbedSubstitutesPlayerAndService() {
        DiscordEmbed embed = VoteDiscordNotifier.buildVoteEmbed(
                "{player} just voted on {service}!", new VoteReceived(ALICE, "PlanetMinecraft"));

        assertThat(embed.title()).isEqualTo("New Vote");
        assertThat(embed.description()).isEqualTo("Alice just voted on PlanetMinecraft!");
    }

    @Test
    void buildPartyEmbedSubstitutesThreshold() {
        DiscordEmbed embed = VoteDiscordNotifier.buildPartyEmbed(
                "The vote party fired at {threshold} votes!", new VotePartyTriggered(25));

        assertThat(embed.title()).isEqualTo("Vote Party");
        assertThat(embed.description()).isEqualTo("The vote party fired at 25 votes!");
    }

    @Test
    void buildTopVoterEmbedListsOneRankedFieldPerRow() {
        Function<UUID, String> resolver = uuid -> uuid.equals(ALICE.uuid()) ? "Alice" : "Bob";
        List<VoteRanking> rankings = List.of(new VoteRanking(ALICE, 42), new VoteRanking(BOB, 7));

        DiscordEmbed embed = VoteDiscordNotifier.buildTopVoterEmbed("Top Voters", rankings, resolver);

        assertThat(embed.title()).isEqualTo("Top Voters");
        assertThat(embed.fields()).hasSize(2);
        assertThat(embed.fields().get(0).name()).isEqualTo("#1 Alice");
        assertThat(embed.fields().get(0).value()).isEqualTo("42");
        assertThat(embed.fields().get(1).name()).isEqualTo("#2 Bob");
        assertThat(embed.fields().get(1).value()).isEqualTo("7");
    }

    @Test
    void malformedWebhookUrlLeavesNotifierInactiveWithoutThrowing() {
        // enabled is true (non-blank URL) but the URL is not a valid http/https URL, so the webhook
        // constructor throws; the notifier must swallow it and mark itself inactive.
        VoteDiscordSettings settings = settings("not-a-valid-url");

        VoteDiscordNotifier notifier = new VoteDiscordNotifier(settings, new SilentLogger());

        assertThat(notifier.active()).isFalse();
        // An inactive notifier accepts events as no-ops without throwing.
        assertThatCode(() -> notifier.accept(new VoteReceived(ALICE, "site"))).doesNotThrowAnyException();
        assertThatCode(() -> notifier.accept(new VotePartyTriggered(10))).doesNotThrowAnyException();
        assertThatCode(() -> notifier.postTopVoter(List.of(new VoteRanking(ALICE, 1)), UUID::toString))
                .doesNotThrowAnyException();
    }

    @Test
    void disabledSettingsLeaveNotifierInactive() {
        VoteDiscordSettings settings = settings("");

        VoteDiscordNotifier notifier = new VoteDiscordNotifier(settings, new SilentLogger());

        assertThat(notifier.active()).isFalse();
    }

    @Test
    void inactiveNotifierIgnoresEveryEvent() {
        VoteDiscordNotifier notifier = new VoteDiscordNotifier(settings(""), new SilentLogger());

        // A disabled notifier never throws regardless of the event handed to it.
        assertThatCode(() -> notifier.accept(new VoteReceived(ALICE, "site"))).doesNotThrowAnyException();
        assertThatCode(() -> notifier.accept(new VotePartyTriggered(5))).doesNotThrowAnyException();
    }

    private static VoteDiscordSettings settings(String url) {
        boolean enabled = !url.isBlank();
        return new VoteDiscordSettings(
                enabled,
                url,
                true,
                "{player} voted on {service}",
                true,
                "party {threshold}",
                new TopVoter(false, VotePeriod.MONTHLY, 10, 1440, "Top Voters"));
    }

    private static final class SilentLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

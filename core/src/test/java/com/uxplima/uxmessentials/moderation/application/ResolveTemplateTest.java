package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.PunishmentTemplate;
import org.junit.jupiter.api.Test;

/**
 * Pure resolution of a punishment-template name to its configured {@link PunishmentTemplate}: a timed template
 * resolves to its reason + span, a permanent one to its reason with no span, the lookup is case-insensitive,
 * and an unknown name is the typed {@link ModerationError#UNKNOWN_TEMPLATE}.
 */
class ResolveTemplateTest {

    private final ResolveTemplate resolve = new ResolveTemplate(Map.of(
            "griefing", PunishmentTemplate.timed("griefing", "Griefing", Duration.ofDays(7)),
            "cheating", PunishmentTemplate.permanent("cheating", "Cheating")));

    @Test
    void resolvesATimedTemplateToItsReasonAndDuration() {
        var result = resolve.resolve("griefing");

        assertThat(result.isOk()).isTrue();
        PunishmentTemplate template = result.orElseThrow();
        assertThat(template.reason()).isEqualTo("Griefing");
        assertThat(template.duration()).contains(Duration.ofDays(7));
    }

    @Test
    void resolvesAPermanentTemplateToItsReasonWithNoDuration() {
        var result = resolve.resolve("cheating");

        assertThat(result.isOk()).isTrue();
        assertThat(result.orElseThrow().duration()).isEqualTo(Optional.empty());
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertThat(resolve.resolve("GRIEFING").isOk()).isTrue();
    }

    @Test
    void anUnknownTemplateIsAModelledError() {
        var result = resolve.resolve("nope");

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.UNKNOWN_TEMPLATE);
    }

    @Test
    void namesListsTheConfiguredTemplatesSorted() {
        assertThat(resolve.names()).containsExactly("cheating", "griefing");
    }
}

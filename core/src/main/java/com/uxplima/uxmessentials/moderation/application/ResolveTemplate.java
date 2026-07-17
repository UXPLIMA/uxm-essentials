package com.uxplima.uxmessentials.moderation.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.PunishmentTemplate;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * Resolves a punishment-template name to its configured {@link PunishmentTemplate}. Pure: it holds the
 * templates the operator declared under {@code moderation.conf} {@code templates} (parsed and validated once
 * at wire time) and answers a name lookup with no ports and no side effects, so it is unit-tested against a
 * fixture map. An unknown name is a first-class {@link ModerationError#UNKNOWN_TEMPLATE} the caller renders
 * to the actor rather than a thrown fault.
 *
 * <p>Lookup is case-insensitive so {@code /punish Player Griefing} matches a {@code griefing} template; the
 * template's own name preserves the operator's original casing for display.
 */
public final class ResolveTemplate {

    private final Map<String, PunishmentTemplate> byName;

    public ResolveTemplate(Map<String, PunishmentTemplate> templates) {
        Objects.requireNonNull(templates, "templates");
        Map<String, PunishmentTemplate> folded = new LinkedHashMap<>();
        templates.forEach((name, template) -> {
            Objects.requireNonNull(name, "template name");
            Objects.requireNonNull(template, "template");
            folded.put(name.toLowerCase(Locale.ROOT), template);
        });
        this.byName = Map.copyOf(folded);
    }

    /** Resolve {@code name} to its template, or {@link ModerationError#UNKNOWN_TEMPLATE} when none is configured. */
    public Result<PunishmentTemplate, ModerationError> resolve(String name) {
        Objects.requireNonNull(name, "name");
        PunishmentTemplate template = byName.get(name.toLowerCase(Locale.ROOT));
        return template == null ? Result.err(ModerationError.UNKNOWN_TEMPLATE) : Result.ok(template);
    }

    /** The configured template names (original casing), sorted, for command tab-completion. */
    public List<String> names() {
        return byName.values().stream()
                .map(PunishmentTemplate::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}

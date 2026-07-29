package com.uxplima.uxmessentials.shared.adapter.outbound.integration;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * What an integration is for, from an operator's point of view. The family is how {@code /uxmess doctor}
 * groups its report and how the documentation orders its table, so the labels read as an operator would say
 * them rather than as the package tree spells them.
 */
@NullMarked
public enum IntegrationFamily {
    PERMISSIONS("permissions"),
    ECONOMY("economy"),
    PLACEHOLDERS("placeholders"),
    ITEMS("custom items"),
    CLAIMS("land claims"),
    REGIONS("regions"),
    MAPS("web maps"),
    VOTE("voting"),
    BEDROCK("bedrock"),
    LOGIN("login"),
    VANISH("vanish"),
    COMBAT("combat"),
    PROTOCOL("client protocol"),
    CONDITIONS("menu conditions");

    private final String label;

    IntegrationFamily(String label) {
        this.label = Objects.requireNonNull(label, "label");
    }

    /** The operator-facing name of this family, lower case so it reads inside a sentence. */
    public String label() {
        return label;
    }
}

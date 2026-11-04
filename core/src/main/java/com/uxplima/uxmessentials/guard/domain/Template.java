package com.uxplima.uxmessentials.guard.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A named set of rules and behaviours a region links to. The region's own entries sit on top; editing the
 * template in config and reloading changes every linked region.
 *
 * @param name the template name, lower case
 * @param rules its rules
 * @param behaviours its behaviours
 */
public record Template(String name, List<Rule> rules, Behaviours behaviours) {

    public Template {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(behaviours, "behaviours");
        name = name.strip().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("template name must not be blank");
        }
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }
}

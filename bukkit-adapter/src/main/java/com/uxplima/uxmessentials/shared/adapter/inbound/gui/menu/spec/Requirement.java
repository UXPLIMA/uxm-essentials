package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.Objects;

/**
 * A single requirement inside a {@link RequirementSpec}: a condition {@link Ref} the runtime evaluates and whether
 * that outcome is negated. A condition may carry a value ({@code has-money:100}), so the ref keeps its args; the
 * {@code inverted} flag comes from a leading {@code !} in the config token, letting an author gate on the absence of
 * something ({@code !has-empty-slots:1} passes exactly when the inventory is full).
 *
 * <p>Pure by design: this holds only the parsed intent. Evaluating the condition needs the condition registry, which
 * lives in the runtime, so the negation is applied there — this record stays Bukkit-free for plain-JUnit testing.
 */
public record Requirement(Ref condition, boolean inverted) {

    public Requirement {
        Objects.requireNonNull(condition, "condition");
    }
}

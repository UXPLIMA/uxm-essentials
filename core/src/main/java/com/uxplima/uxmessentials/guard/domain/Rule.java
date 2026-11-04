package com.uxplima.uxmessentials.guard.domain;

import java.util.Objects;

/**
 * One protection rule: {@code effect} for {@code target} on {@code action} and everything under it.
 *
 * @param action the action tree node the rule covers
 * @param target who the rule applies to
 * @param effect allow or deny
 */
public record Rule(ActionId action, Target target, Effect effect) {

    public Rule {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(effect, "effect");
    }

    /** True when this rule and {@code other} name the same action and target, so one replaces the other. */
    public boolean sameSlot(Rule other) {
        return action.equals(other.action) && target.equals(other.target);
    }

    @Override
    public String toString() {
        return action + " " + target.text() + " " + effect.word();
    }
}

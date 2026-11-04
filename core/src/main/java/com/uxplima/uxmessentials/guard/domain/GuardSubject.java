package com.uxplima.uxmessentials.guard.domain;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Who is acting, as the engine needs to see them: an id for roles, and answers for permission and condition
 * targets. Nature acts as {@link #WORLD}, which holds no role, no permission and meets no condition.
 */
public interface GuardSubject {

    /** The player's id, or {@code null} for nature. */
    @Nullable UUID id();

    /** True when the subject holds {@code node}. */
    boolean has(String node);

    /** True when the subject meets the condition {@code expression}. */
    boolean meets(String expression);

    /** Nature: fire, water, pistons, mobs. */
    GuardSubject WORLD = new GuardSubject() {
        @Override
        public @Nullable UUID id() {
            return null;
        }

        @Override
        public boolean has(String node) {
            return false;
        }

        @Override
        public boolean meets(String expression) {
            return false;
        }

        @Override
        public String toString() {
            return "world";
        }
    };
}

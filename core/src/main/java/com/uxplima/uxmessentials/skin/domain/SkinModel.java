package com.uxplima.uxmessentials.skin.domain;

import org.jspecify.annotations.NullMarked;

/**
 * Which of the two player models a skin is drawn on: the classic four-pixel arm, or the slim three-pixel one
 * Mojang calls Alex. A texture uploaded for the wrong model renders with a seam down the arm, so the choice
 * travels with the texture rather than being guessed from it.
 */
@NullMarked
public enum SkinModel {
    CLASSIC,
    SLIM;

    /** The model a {@code slim} flag stands for, as every skin service spells it. */
    public static SkinModel of(boolean slim) {
        return slim ? SLIM : CLASSIC;
    }

    /** Whether this is the three-pixel-arm model. */
    public boolean slim() {
        return this == SLIM;
    }
}

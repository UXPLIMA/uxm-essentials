package com.uxplima.uxmessentials.npc.adapter.outbound;

import org.jspecify.annotations.NullMarked;

/**
 * The yaw/pitch a fake player must adopt to face a target point, in Minecraft's degree convention. Pure
 * geometry with no Bukkit reference, so the look-at-player rotation is unit-testable on its own: the renderer
 * feeds it the NPC's eye position and the viewer's eye position and sends the result through the packet stack.
 *
 * <p>Minecraft yaw is measured clockwise from the negative-Z axis (south is 0°, west is 90°), so the horizontal
 * angle from the NPC toward the target is {@code atan2(-dx, dz)}; pitch is positive looking down, so it is
 * {@code atan2(-dy, horizontalDistance)}. A target directly on top of the NPC has no horizontal direction, so
 * the yaw is left unchanged (the caller passes the NPC's current yaw) and only the pitch swings to vertical.
 *
 * @param yaw the horizontal look angle in degrees
 * @param pitch the vertical look angle in degrees, positive looking down
 */
@NullMarked
public record LookAngles(float yaw, float pitch) {

    /**
     * The angles that aim a fake player standing eye-height at {@code fromX,fromY,fromZ} toward the eye point
     * {@code toX,toY,toZ}. {@code currentYaw} is returned for the horizontal angle only when the target sits
     * directly above or below (no horizontal direction to face).
     */
    public static LookAngles facing(
            double fromX, double fromY, double fromZ, double toX, double toY, double toZ, float currentYaw) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double horizontal = Math.sqrt((dx * dx) + (dz * dz));
        float yaw = horizontal < 1.0e-4 ? currentYaw : (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, horizontal));
        return new LookAngles(yaw, pitch);
    }
}

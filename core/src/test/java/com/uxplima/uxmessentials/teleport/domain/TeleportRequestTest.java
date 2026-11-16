package com.uxplima.uxmessentials.teleport.domain;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.testing.Fixtures;
import org.junit.jupiter.api.Test;

class TeleportRequestTest {

    private static final Instant EXPIRES = NOW.plus(Duration.ofMinutes(2));

    private static TeleportRequest open(RequestDirection direction) {
        return TeleportRequest.open(ALEX, SAM, direction, EXPIRES).request();
    }

    @Test
    void aTpaMovesTheRequesterAndATpahereMovesTheTarget() {
        assertThat(open(RequestDirection.TO_TARGET).mover()).isEqualTo(ALEX);
        assertThat(open(RequestDirection.TO_TARGET).anchor()).isEqualTo(SAM);
        assertThat(open(RequestDirection.TO_REQUESTER).mover()).isEqualTo(SAM);
        assertThat(open(RequestDirection.TO_REQUESTER).anchor()).isEqualTo(ALEX);
    }

    @Test
    void aPendingRequestCanBeAcceptedBeforeItExpires() {
        TeleportRequest request = open(RequestDirection.TO_TARGET);

        TeleportRequest.Transition accepted = request.accept(NOW.plusSeconds(30));

        assertThat(accepted.request().phase()).isEqualTo(RequestState.Phase.ACCEPTED);
        assertThat(request.phase()).isEqualTo(RequestState.Phase.PENDING);
        assertThatThrownBy(() -> accepted.request().deny()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anExpiredRequestCannotBeAcceptedOnlyExpired() {
        TeleportRequest request = open(RequestDirection.TO_TARGET);

        assertThatThrownBy(() -> request.accept(EXPIRES)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.expire(EXPIRES.minusSeconds(1))).isInstanceOf(IllegalStateException.class);
        assertThat(request.expire(EXPIRES).request().phase()).isEqualTo(RequestState.Phase.EXPIRED);
    }

    @Test
    void denyAndCancelEndTheRequest() {
        assertThat(open(RequestDirection.TO_TARGET).deny().request().phase()).isEqualTo(RequestState.Phase.DENIED);
        assertThat(open(RequestDirection.TO_TARGET).cancel().request().phase()).isEqualTo(RequestState.Phase.CANCELLED);
    }

    @Test
    void aPlayerCannotAskThemselves() {
        assertThatThrownBy(() -> TeleportRequest.open(ALEX, ALEX, RequestDirection.TO_TARGET, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWarmupIsCancelledByMovingPastTheThresholdOnly() {
        WarmupCancelToggles toggles = new WarmupCancelToggles(true, false, true, false, 1.5);
        PendingTeleport pending = PendingTeleport.arm(
                ALEX, TeleportKind.HOME, at(0.5, 64, 0.5), Destination.at(at(100, 64, 100)), toggles);

        assertThat(pending.onMovement(at(0.9, 64, 0.5)).didCancel()).isFalse();
        assertThat(pending.onMovement(at(1.5, 64, 0.5)).didCancel()).isFalse();
        PendingTeleport.Outcome moved = pending.onMovement(at(3.5, 64, 0.5));
        assertThat(moved.cancel()).contains(WarmupCancelReason.MOVED);
        assertThat(moved.state().isCancelled()).isTrue();
        assertThat(moved.state().onAction(WarmupCancelReason.DAMAGED).didCancel())
                .isFalse();
    }

    @Test
    void onlyTheArmedActionsCancelAWarmup() {
        PendingTeleport pending = PendingTeleport.arm(
                ALEX, TeleportKind.WARP, at(0, 64, 0), Destination.at(at(10, 64, 10)), WarmupCancelToggles.defaults());

        assertThat(pending.onAction(WarmupCancelReason.DAMAGED).didCancel()).isFalse();
        assertThat(pending.onAction(WarmupCancelReason.ABORTED).cancel()).contains(WarmupCancelReason.ABORTED);
        assertThat(pending.onMovement(at(5, 64, 0)).cancel()).contains(WarmupCancelReason.MOVED);
    }

    @Test
    void turningCancelsAWarmupOnlyWhenRotationIsArmed() {
        Position facingNorth = looking(180f, 0f);
        PendingTeleport calm = PendingTeleport.arm(
                ALEX, TeleportKind.HOME, facingNorth, Destination.at(at(10, 64, 10)), WarmupCancelToggles.defaults());
        PendingTeleport strict = PendingTeleport.arm(
                ALEX,
                TeleportKind.HOME,
                facingNorth,
                Destination.at(at(10, 64, 10)),
                new WarmupCancelToggles(true, true, false, false, 0.0));

        assertThat(calm.onMovement(looking(90f, 0f)).didCancel()).isFalse();
        assertThat(strict.onMovement(looking(180f, 0f)).didCancel()).isFalse();
        assertThat(strict.onMovement(looking(180f, 30f)).cancel()).contains(WarmupCancelReason.ROTATED);
        assertThat(strict.onMovement(looking(90f, 0f)).cancel()).contains(WarmupCancelReason.ROTATED);
    }

    @Test
    void aYawThatWrapsAroundIsNotATurn() {
        PendingTeleport strict = PendingTeleport.arm(
                ALEX,
                TeleportKind.HOME,
                looking(-90f, 0f),
                Destination.at(at(10, 64, 10)),
                new WarmupCancelToggles(false, true, false, false, 0.0));

        assertThat(strict.onMovement(looking(270f, 0f)).didCancel()).isFalse();
        assertThat(strict.onMovement(looking(630f, 0f)).didCancel()).isFalse();
    }

    private static Position looking(float yaw, float pitch) {
        return new Position(Fixtures.WORLD, 0.5, 64, 0.5, yaw, pitch);
    }
}

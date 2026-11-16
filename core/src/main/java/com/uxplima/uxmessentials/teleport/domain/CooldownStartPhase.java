package com.uxplima.uxmessentials.teleport.domain;

/**
 * When a teleport cooldown's clock starts, and, equivalently, which lifecycle transitions are allowed
 * to burn it. This is the domain's own statement of the {@code cooldown-start-phase} contract; the
 * shared {@code Cooldowns.CooldownStartPhase} kernel enum mirrors it at the port boundary, and the
 * application maps between the two so the domain never imports a port type.
 *
 * <p>The whole point of the phase is the subtlety most teleport plugins get wrong: with {@link #ACCEPT}
 * or {@link #TELEPORT}, a denied, expired, or self-cancelled request must <em>never</em> burn the
 * requester's cooldown, because the requester never actually moved. {@link #burnsOn(RequestState.Phase)}
 * is the single predicate that encodes that rule, so every use case asks the domain the same question.
 */
public enum CooldownStartPhase {

    /**
     * The clock starts for the sender the moment a {@code /tpa} or {@code /tpahere} is open: even a request later
     * denied burns it. A teleport with no request step starts it once its checks pass and its warmup begins, and keeps
     * it when the warmup is cancelled.
     */
    REQUEST,

    /**
     * The clock starts for the mover when the target accepts; a denied or expired request does not burn it. A
     * teleport with no request step has nothing to accept and starts it as {@link #REQUEST} does.
     */
    ACCEPT,

    /** The clock starts only on arrival; the cooldown is added after the warmup. The safe default. */
    TELEPORT;

    /**
     * Whether reaching {@code phase} stamps the cooldown for this start-phase. {@link #REQUEST} burns
     * as soon as the request is issued; {@link #ACCEPT} burns once accepted; {@link #TELEPORT} burns
     * only on a completed arrival. A request that never reaches the burning phase costs nothing.
     */
    public boolean burnsOn(RequestState.Phase phase) {
        return switch (this) {
            case REQUEST ->
                phase == RequestState.Phase.PENDING
                        || phase == RequestState.Phase.ACCEPTED
                        || phase == RequestState.Phase.COMPLETED;
            case ACCEPT -> phase == RequestState.Phase.ACCEPTED || phase == RequestState.Phase.COMPLETED;
            case TELEPORT -> phase == RequestState.Phase.COMPLETED;
        };
    }

    /** True when the cooldown is folded into the warmup rather than charged before it ({@link #TELEPORT}). */
    public boolean addsCooldownToWarmup() {
        return this == TELEPORT;
    }
}

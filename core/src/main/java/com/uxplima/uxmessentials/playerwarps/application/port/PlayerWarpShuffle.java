package com.uxplima.uxmessentials.playerwarps.application.port;

/**
 * The order the random browse sort lists warps in. Each round deals every warp a new place. A round's order depends
 * only on the round and the warps, so servers that share one database deal the same order for the same round without
 * talking to each other, and dealing a round that is already in place writes nothing.
 *
 * <p>The order lives in storage so a browse can page through it; a caller runs this off the tick thread.
 */
public interface PlayerWarpShuffle {

    /** Give every warp its place for {@code round}; returns how many warps changed place. */
    int shuffle(long round);
}

package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A frame too short to carry a seal is refused without failing, and a seal from too far ahead is refused too. */
class FrameSealTest {

    private static final BusSecret SECRET = BusSecret.of("0123456789abcdef0123456789abcdef-network");
    private static final long START = 1_700_000_000_000L;
    private static final byte[] FRAME = {1, 11, 0, 3, 's', '-', '1'};

    private long now = START;
    private final FrameSeal seal = new FrameSeal(SECRET, () -> now);

    @Test
    void aFrameNoLongerThanTheTrailerIsRefusedAsUnsigned() {
        assertThat(seal.check(new byte[0])).isEqualTo(FrameSeal.Check.SIGNATURE);
        assertThat(seal.check(new byte[FrameSeal.TRAILER_BYTES])).isEqualTo(FrameSeal.Check.SIGNATURE);
        assertThat(seal.check(new byte[] {1})).isEqualTo(FrameSeal.Check.SIGNATURE);
    }

    @Test
    void aFrameSealedTooFarAheadOfThisClockIsRefused() {
        now = START + FrameSeal.CLOCK_TOLERANCE.toMillis() + 1;
        byte[] fromTheFuture = seal.seal(FRAME);
        now = START + FrameSeal.CLOCK_TOLERANCE.toMillis();
        byte[] justAhead = seal.seal(FRAME);

        now = START;

        assertThat(seal.check(fromTheFuture)).isEqualTo(FrameSeal.Check.CLOCK);
        assertThat(seal.check(justAhead)).isEqualTo(FrameSeal.Check.TRUSTED);
        assertThat(FrameSeal.frame(justAhead)).isEqualTo(FRAME);
    }
}

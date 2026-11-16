package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongSupplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jspecify.annotations.NullMarked;

/**
 * Signs the frames a backend sends on the plugin channel and checks the ones it receives, with the shared
 * {@link BusSecret}. Without it a backend cannot tell a frame another backend sent through the proxy broker from one a
 * player's client wrote, since anything in front of the backend that does not stop the channel passes the client's
 * message on.
 *
 * <p>A sealed frame is the frame unchanged, then the time it was sealed (eight bytes, epoch milliseconds) and an
 * HMAC-SHA-256 over {@link #DOMAIN}, the frame and the time. The tag keeps a signature made for this purpose from
 * passing for one made with the same key anywhere else. The trailer goes after the frame so the header the broker
 * reads (version, type, origin) stays where it was, and the broker relays sealed frames without knowing the secret. A
 * decoder that does not know about the trailer stops after the fields it reads and never sees it.
 *
 * <p>The signed time limits replay. Behind the broker a client never sees a backend's frames, but a client connected
 * straight to a backend, or behind a proxy without the broker, receives what that backend sends and could hand it to
 * another backend later. A frame sealed more than {@link #CLOCK_TOLERANCE} from this backend's clock is refused. Within
 * the tolerance, a frame with an id its listener remembers acts once and a trade signal is settled by the shared
 * escrow; a vanish change carries no sequence, so such a client could still hold one back or repeat it inside the
 * tolerance.
 *
 * <p>Stateless apart from the key: a {@link Mac} is made per call because one is not safe to share between the threads
 * that send and receive.
 */
@NullMarked
final class FrameSeal {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MAC_BYTES = 32;

    /** Signed ahead of every frame, so the signature means "a bus frame" and nothing else. */
    private static final byte[] DOMAIN = "uxm-bus-v1".getBytes(StandardCharsets.US_ASCII);

    /** What sealing adds to a frame: the signed time and the signature. */
    static final int TRAILER_BYTES = Long.BYTES + MAC_BYTES;

    /**
     * How far the signed time may be from this backend's clock, either way. Frames are sealed when they leave and the
     * broker keeps no queue, so this covers the clock difference between backends, not the time a frame travels.
     */
    static final Duration CLOCK_TOLERANCE = Duration.ofSeconds(60);

    private final SecretKeySpec key;
    private final LongSupplier clock;

    FrameSeal(BusSecret secret, LongSupplier clock) {
        Objects.requireNonNull(secret, "secret");
        if (!secret.usable()) {
            throw new IllegalArgumentException("the bus secret is blank or too short to sign with");
        }
        this.key = new SecretKeySpec(secret.key(), ALGORITHM);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** {@code frame} with the current time and its signature appended. */
    byte[] seal(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        byte[] sealed = Arrays.copyOf(frame, frame.length + TRAILER_BYTES);
        ByteBuffer.wrap(sealed, frame.length, Long.BYTES).putLong(clock.getAsLong());
        byte[] signature = sign(sealed, frame.length + Long.BYTES);
        System.arraycopy(signature, 0, sealed, frame.length + Long.BYTES, MAC_BYTES);
        return sealed;
    }

    /** Whether {@code sealed} was signed with this secret within the clock tolerance, and if not, why. */
    Check check(byte[] sealed) {
        Objects.requireNonNull(sealed, "sealed");
        if (sealed.length <= TRAILER_BYTES) {
            return Check.SIGNATURE;
        }
        int signed = sealed.length - MAC_BYTES;
        byte[] expected = sign(sealed, signed);
        byte[] actual = Arrays.copyOfRange(sealed, signed, sealed.length);
        // Constant time: a comparison that stops at the first wrong byte tells a forger how much of a guess was right.
        if (!MessageDigest.isEqual(expected, actual)) {
            return Check.SIGNATURE;
        }
        long sealedAt = ByteBuffer.wrap(sealed, signed - Long.BYTES, Long.BYTES).getLong();
        long drift = Math.abs(clock.getAsLong() - sealedAt);
        // A negative drift is an overflow from a time far outside any clock; it is as stale as a large one.
        return drift < 0 || drift > CLOCK_TOLERANCE.toMillis() ? Check.CLOCK : Check.TRUSTED;
    }

    /** The frame inside {@code sealed}, without the trailer; only meaningful once {@link #check} trusted it. */
    static byte[] frame(byte[] sealed) {
        Objects.requireNonNull(sealed, "sealed");
        return Arrays.copyOf(sealed, Math.max(0, sealed.length - TRAILER_BYTES));
    }

    private byte[] sign(byte[] bytes, int length) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            mac.update(DOMAIN);
            mac.update(bytes, 0, length);
            return mac.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException cryptoUnavailable) {
            // HmacSHA256 is mandated on every JDK; a provider without it must fail loud, not let frames through.
            throw new IllegalStateException(
                    "JDK is missing the mandatory " + ALGORITHM + " provider", cryptoUnavailable);
        }
    }

    /** What checking a frame found. */
    enum Check {
        /** Signed with this secret, within the clock tolerance. */
        TRUSTED,
        /** Not signed with this secret: unsigned, signed with another secret, or changed on the way. */
        SIGNATURE,
        /** Signed with this secret, but further from this backend's clock than the tolerance allows. */
        CLOCK
    }
}

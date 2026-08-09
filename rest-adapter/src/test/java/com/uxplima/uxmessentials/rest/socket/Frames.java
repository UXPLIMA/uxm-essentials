package com.uxplima.uxmessentials.rest.socket;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Frames as a client sends them: masked, which is the half {@link Frame} only reads and never writes.
 *
 * <p>The mask is a fixed four bytes rather than a random one. A test that fails should fail the same way twice,
 * and nothing here depends on the mask being unguessable.
 */
final class Frames {

    private static final byte[] MASK = {0x12, 0x34, 0x56, 0x78};

    private Frames() {}

    /** A masked text frame, as a browser would put it on the wire. */
    static byte[] clientText(String message) {
        return masked(Frame.TEXT, message.getBytes(StandardCharsets.UTF_8));
    }

    /** A masked close frame with a code and no reason. */
    static byte[] clientClose(int code) {
        return masked(Frame.CLOSE, new byte[] {(byte) (code >> 8), (byte) code});
    }

    /** A masked ping carrying nothing. */
    static byte[] clientPing() {
        return masked(Frame.PING, new byte[0]);
    }

    /**
     * Read a frame the way a client reads one: unmasked, which is the half {@link Frame} only writes.
     *
     * <p>The other end of the protocol, written out here rather than bent into the server's reader, because a
     * reader that accepted unmasked frames to make the tests easier would be the bug the tests are for.
     */
    static Frame serverFrame(InputStream in) throws IOException {
        int first = next(in);
        int second = next(in);
        if ((second & 0x80) != 0) {
            throw new IOException("a server frame should not be masked");
        }
        int length = second & 0x7F;
        if (length == 126) {
            length = (next(in) << 8) | next(in);
        } else if (length == 127) {
            throw new IOException("nothing in these tests sends a frame that long");
        }
        byte[] payload = in.readNBytes(length);
        if (payload.length < length) {
            throw new EOFException("the frame ended early");
        }
        return new Frame(first & 0x0F, payload);
    }

    private static int next(InputStream in) throws IOException {
        int read = in.read();
        if (read < 0) {
            throw new EOFException("the connection ended");
        }
        return read;
    }

    private static byte[] masked(int opcode, byte[] payload) {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x80 | opcode);
        if (payload.length < 126) {
            frame.write(0x80 | payload.length);
        } else {
            frame.write(0x80 | 126);
            frame.write(payload.length >> 8);
            frame.write(payload.length & 0xFF);
        }
        frame.writeBytes(MASK);
        for (int at = 0; at < payload.length; at++) {
            frame.write(payload[at] ^ MASK[at % 4]);
        }
        return frame.toByteArray();
    }
}

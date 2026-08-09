package com.uxplima.uxmessentials.rest.socket;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * One WebSocket frame, read off a socket or written to one.
 *
 * <p>The framing is small enough to write out plainly, and doing so keeps a dependency out of a jar whose whole
 * point is that it is optional. What it supports is what an event stream needs: whole text messages one way, and
 * text, ping, pong and close the other.
 *
 * <p>Two deliberate limits. A frame from a client must be masked, which the specification requires and which a
 * server that skips the check turns into a proxy-poisoning trick. And a fragmented message is refused rather than
 * reassembled: the only thing a client sends here is a short subscription line, so a continuation frame is either a
 * mistake or somebody probing, and buffering an unbounded number of them is how a listener runs out of memory.
 *
 * <p>A class rather than a record because the payload is a byte array: an accessor handing the same array to every
 * caller is not the value semantics a record promises, and there is one reader per connection so there is no reason
 * to copy it either.
 */
public final class Frame {

    /** A whole text message. */
    public static final int TEXT = 0x1;

    /** The other end is closing, with a code and possibly a reason. */
    public static final int CLOSE = 0x8;

    /** Are you there. */
    public static final int PING = 0x9;

    /** Yes. */
    public static final int PONG = 0xA;

    /** A continuation of a fragmented message, which this refuses. */
    private static final int CONTINUATION = 0x0;

    /** The largest frame this reads. A subscription line is a few hundred bytes; this is room to spare. */
    static final int MAX_PAYLOAD_BYTES = 64 * 1024;

    /** Going away, which is what a listener shutting down means. */
    public static final int CLOSE_GOING_AWAY = 1001;

    /** The other end sent something this does not implement. */
    public static final int CLOSE_UNSUPPORTED = 1003;

    /** The other end broke the protocol. */
    public static final int CLOSE_PROTOCOL_ERROR = 1002;

    /** A frame bigger than this will read. */
    public static final int CLOSE_TOO_BIG = 1009;

    private final int opcode;
    private final byte[] payload;

    Frame(int opcode, byte[] payload) {
        this.opcode = opcode;
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    /** The frame type, as it appears on the wire. */
    public int opcode() {
        return opcode;
    }

    /** The frame's bytes, already unmasked. Not copied: one reader owns a connection. */
    public byte[] payload() {
        return payload;
    }

    /** The payload as text, for the frames that carry any. */
    public String text() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    /** A text frame carrying {@code message}, unmasked, as a server sends. */
    public static byte[] text(String message) {
        return frame(TEXT, message.getBytes(StandardCharsets.UTF_8));
    }

    /** A pong echoing what a ping carried, which is what the specification asks for. */
    public static byte[] pong(byte[] payload) {
        return frame(PONG, payload);
    }

    /** An empty ping, to find out whether anybody is still there. */
    public static byte[] ping() {
        return frame(PING, new byte[0]);
    }

    /** A close frame carrying a code and a short reason. */
    public static byte[] close(int code, String reason) {
        byte[] words = reason.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[2 + words.length];
        payload[0] = (byte) (code >> 8);
        payload[1] = (byte) code;
        System.arraycopy(words, 0, payload, 2, words.length);
        return frame(CLOSE, payload);
    }

    /**
     * Read one frame.
     *
     * @throws EOFException when the connection ended, which is an ordinary way for a stream to finish
     * @throws ProtocolException when the client broke the protocol, carrying the close code to answer with
     */
    public static Frame read(InputStream in) throws IOException {
        int first = next(in);
        boolean fin = (first & 0x80) != 0;
        int opcode = first & 0x0F;
        if (!fin || opcode == CONTINUATION) {
            throw new ProtocolException(CLOSE_UNSUPPORTED, "this listener does not read fragmented messages");
        }

        int second = next(in);
        if ((second & 0x80) == 0) {
            throw new ProtocolException(CLOSE_PROTOCOL_ERROR, "a frame from a client has to be masked");
        }
        long length = lengthOf(in, second & 0x7F);
        byte[] mask = in.readNBytes(4);
        if (mask.length < 4) {
            throw new EOFException("the connection ended inside a frame");
        }
        byte[] payload = in.readNBytes((int) length);
        if (payload.length < length) {
            throw new EOFException("the connection ended inside a frame");
        }
        for (int at = 0; at < payload.length; at++) {
            payload[at] = (byte) (payload[at] ^ mask[at % 4]);
        }
        return new Frame(opcode, payload);
    }

    private static long lengthOf(InputStream in, int declared) throws IOException {
        long length = declared;
        if (declared == 126) {
            length = ((long) next(in) << 8) | next(in);
        } else if (declared == 127) {
            length = 0;
            for (int at = 0; at < 8; at++) {
                length = (length << 8) | next(in);
            }
        }
        if (length < 0 || length > MAX_PAYLOAD_BYTES) {
            throw new ProtocolException(CLOSE_TOO_BIG, "a frame longer than " + MAX_PAYLOAD_BYTES + " bytes");
        }
        return length;
    }

    private static int next(InputStream in) throws IOException {
        int read = in.read();
        if (read < 0) {
            throw new EOFException("the connection ended between frames");
        }
        return read;
    }

    /** Build an unmasked frame, which is the only kind a server sends. */
    private static byte[] frame(int opcode, byte[] payload) {
        byte[] header = headerFor(opcode, payload.length);
        byte[] whole = new byte[header.length + payload.length];
        System.arraycopy(header, 0, whole, 0, header.length);
        System.arraycopy(payload, 0, whole, header.length, payload.length);
        return whole;
    }

    private static byte[] headerFor(int opcode, int length) {
        byte first = (byte) (0x80 | opcode);
        if (length < 126) {
            return new byte[] {first, (byte) length};
        }
        if (length < 0x1_0000) {
            return new byte[] {first, 126, (byte) (length >> 8), (byte) length};
        }
        return new byte[] {
            first, 127, 0, 0, 0, 0, (byte) (length >> 24), (byte) (length >> 16), (byte) (length >> 8), (byte) length
        };
    }

    /** A client that broke the protocol, and the close code that says which way. */
    public static final class ProtocolException extends IOException {

        private static final long serialVersionUID = 1L;

        private final int closeCode;

        ProtocolException(int closeCode, String message) {
            super(message);
            this.closeCode = closeCode;
        }

        /** The WebSocket close code to send before hanging up. */
        public int closeCode() {
            return closeCode;
        }
    }
}

package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Just enough of the NBT format to edit a few tags of a file and leave the rest alone. A compound is read as its
 * entries in file order, each a type, a name and the payload bytes as they were; a list as its element type and its
 * elements' payload bytes. Nothing below the level being edited is decoded, so writing an untouched tag back is
 * copying its bytes, and no tag the server knows and this class does not can be lost on the way.
 */
final class RawNbt {

    static final byte END = 0;
    static final byte BYTE = 1;
    static final byte SHORT = 2;
    static final byte INT = 3;
    static final byte LONG = 4;
    static final byte FLOAT = 5;
    static final byte DOUBLE = 6;
    static final byte BYTE_ARRAY = 7;
    static final byte STRING = 8;
    static final byte LIST = 9;
    static final byte COMPOUND = 10;
    static final byte INT_ARRAY = 11;
    static final byte LONG_ARRAY = 12;

    /** The nesting the server itself allows; deeper is a broken file, not a deep one. */
    private static final int MAX_DEPTH = 512;

    private RawNbt() {}

    /** One entry of a compound: its tag type, its name and its payload. */
    record Entry(byte type, String name, TagPayload payload) {

        Entry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(payload, "payload");
        }
    }

    /** A list: the type of its elements and each element's payload, in order. */
    record ListTag(byte elementType, List<TagPayload> elements) {

        ListTag {
            elements = List.copyOf(elements);
        }
    }

    /** A whole file: the root compound's name and its entries. */
    record Root(String name, List<Entry> entries) {

        Root {
            Objects.requireNonNull(name, "name");
            entries = List.copyOf(entries);
        }
    }

    /** Read an uncompressed file, which must be one named root compound. */
    static Root root(byte[] file) throws IOException {
        Reader reader = new Reader(file);
        if (reader.in.readByte() != COMPOUND) {
            throw new IOException("the root tag is not a compound");
        }
        String name = reader.in.readUTF();
        return new Root(name, reader.entries(0));
    }

    /** Write {@code root} back as an uncompressed file. */
    static byte[] root(Root root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(COMPOUND);
        out.writeUTF(root.name());
        writeEntries(out, root.entries());
        out.flush();
        return bytes.toByteArray();
    }

    /** The entries of a compound payload. */
    static List<Entry> entries(TagPayload compound) throws IOException {
        Reader reader = new Reader(compound.shared());
        List<Entry> entries = reader.entries(0);
        reader.requireEnd();
        return entries;
    }

    /** A compound payload holding {@code entries}. */
    static TagPayload compound(List<Entry> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeEntries(out, entries);
        out.flush();
        return new TagPayload(bytes.toByteArray());
    }

    /** The element type and elements of a list payload. */
    static ListTag list(TagPayload list) throws IOException {
        Reader reader = new Reader(list.shared());
        byte type = reader.in.readByte();
        int length = reader.length();
        List<TagPayload> elements = new ArrayList<>(Math.min(length, 1024));
        for (int i = 0; i < length; i++) {
            elements.add(reader.payload(type, 1));
        }
        reader.requireEnd();
        return new ListTag(type, elements);
    }

    /** A list payload holding {@code list}. */
    static TagPayload list(ListTag list) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(list.elements().isEmpty() ? END : list.elementType());
        out.writeInt(list.elements().size());
        for (TagPayload element : list.elements()) {
            out.write(element.shared());
        }
        out.flush();
        return new TagPayload(bytes.toByteArray());
    }

    /** The payload of a single byte tag. */
    static TagPayload byteValue(int value) {
        return new TagPayload(new byte[] {(byte) value});
    }

    /** The value of a whole-number tag; any other type is a broken file here. */
    static long number(byte type, TagPayload payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload.shared()));
        return switch (type) {
            case BYTE -> in.readByte();
            case SHORT -> in.readShort();
            case INT -> in.readInt();
            case LONG -> in.readLong();
            default -> throw new IOException("tag type " + type + " is not a whole number");
        };
    }

    private static void writeEntries(DataOutputStream out, List<Entry> entries) throws IOException {
        for (Entry entry : entries) {
            out.writeByte(entry.type());
            out.writeUTF(entry.name());
            out.write(entry.payload().shared());
        }
        out.writeByte(END);
    }

    /** Walks a byte array, slicing each payload out as it steps over it. */
    private static final class Reader {

        private final byte[] data;
        private final ByteArrayInputStream source;
        private final DataInputStream in;

        Reader(byte[] data) {
            this.data = data;
            this.source = new ByteArrayInputStream(data);
            this.in = new DataInputStream(source);
        }

        private int position() {
            return data.length - source.available();
        }

        List<Entry> entries(int depth) throws IOException {
            List<Entry> entries = new ArrayList<>();
            for (byte type = in.readByte(); type != END; type = in.readByte()) {
                String name = in.readUTF();
                entries.add(new Entry(type, name, payload(type, depth + 1)));
            }
            return entries;
        }

        TagPayload payload(byte type, int depth) throws IOException {
            int start = position();
            skip(type, depth);
            return new TagPayload(Arrays.copyOfRange(data, start, position()));
        }

        void requireEnd() throws IOException {
            if (source.available() != 0) {
                throw new IOException(source.available() + " bytes after the end of the tag");
            }
        }

        int length() throws IOException {
            int length = in.readInt();
            if (length < 0) {
                throw new IOException("negative length " + length);
            }
            return length;
        }

        private void skip(byte type, int depth) throws IOException {
            if (depth > MAX_DEPTH) {
                throw new IOException("tags nested deeper than " + MAX_DEPTH);
            }
            switch (type) {
                case BYTE -> skipFully(1);
                case SHORT -> skipFully(2);
                case INT, FLOAT -> skipFully(4);
                case LONG, DOUBLE -> skipFully(8);
                case BYTE_ARRAY -> skipFully(length());
                case STRING -> skipFully(in.readUnsignedShort());
                case INT_ARRAY -> skipFully(4L * length());
                case LONG_ARRAY -> skipFully(8L * length());
                case LIST -> {
                    byte element = in.readByte();
                    int length = length();
                    for (int i = 0; i < length; i++) {
                        skip(element, depth + 1);
                    }
                }
                case COMPOUND -> {
                    for (byte entry = in.readByte(); entry != END; entry = in.readByte()) {
                        skipFully(in.readUnsignedShort());
                        skip(entry, depth + 1);
                    }
                }
                default -> throw new IOException("unknown tag type " + type);
            }
        }

        /** Step over exactly {@code count} bytes, or fail: a file that ends early must not read as complete. */
        private void skipFully(long count) throws IOException {
            if (count > source.available()) {
                throw new IOException("the data ends " + (count - source.available()) + " bytes early");
            }
            long left = count;
            while (left > 0) {
                left -= in.skipBytes((int) Math.min(left, Integer.MAX_VALUE));
            }
        }
    }
}

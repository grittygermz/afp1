package com.afp.replacement.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Big-endian byte-writing helpers for AFP structured fields.
 */
public final class ByteWriter {

    private ByteWriter() {}

    /** Writes a 16-bit big-endian value into a byte array at the given offset. */
    public static int write16be(byte[] dest, int offset, int value) {
        dest[offset]     = (byte) ((value >> 8) & 0xFF);
        dest[offset + 1] = (byte) (value & 0xFF);
        return offset + 2;
    }

    /** Writes a 24-bit big-endian value into a byte array at the given offset. */
    public static int write24be(byte[] dest, int offset, int value) {
        dest[offset]     = (byte) ((value >> 16) & 0xFF);
        dest[offset + 1] = (byte) ((value >> 8) & 0xFF);
        dest[offset + 2] = (byte) (value & 0xFF);
        return offset + 3;
    }

    /** Writes a 16-bit big-endian value to an output stream. */
    public static void write16(OutputStream out, int value) throws IOException {
        out.write((byte) ((value >> 8) & 0xFF));
        out.write((byte) (value & 0xFF));
    }

    /** Writes a 24-bit big-endian value to an output stream. */
    public static void write24(OutputStream out, int value) throws IOException {
        out.write((byte) ((value >> 16) & 0xFF));
        out.write((byte) ((value >> 8) & 0xFF));
        out.write((byte) (value & 0xFF));
    }

    /** Copies a source array into dest at offset, returning the new offset. */
    public static int copyInto(byte[] dest, int offset, byte[] src) {
        System.arraycopy(src, 0, dest, offset, src.length);
        return offset + src.length;
    }
}

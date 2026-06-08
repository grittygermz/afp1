package com.afp.replacement.util;

import java.nio.charset.StandardCharsets;

/**
 * Converts ASCII strings to the EBCDIC byte representation used in AFP
 * structured-field name fields (e.g. "OGL GOCA" for BGR/BOC object names).
 */
public final class EbcdicEncoder {

    private static final byte EBCDIC_SPACE = 0x40;

    private EbcdicEncoder() {}

    /**
     * Converts an ASCII string to an EBCDIC byte array of the given length,
     * padded with EBCDIC spaces (0x40) or truncated as needed.
     */
    public static byte[] encode(String text, int length) {
        byte[] result = new byte[length];
        byte[] src    = text.getBytes(StandardCharsets.ISO_8859_1);
        for (int i = 0; i < length; i++) {
            result[i] = (i < src.length) ? src[i] : EBCDIC_SPACE;
        }
        return result;
    }
}

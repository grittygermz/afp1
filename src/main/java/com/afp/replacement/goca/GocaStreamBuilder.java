package com.afp.replacement.goca;

import static com.afp.replacement.util.ByteWriter.copyInto;
import static com.afp.replacement.util.ByteWriter.write16be;

/**
 * Builds the GOCA drawing-order byte stream for one image strip.
 *
 * <p>The stream structure (matching the papy reference file):
 * <pre>
 *   GOCA prolog (21 bytes)
 *     BeginSegmentCommand + NOP + GSLE + GSLT + GSMT
 *   GSPCOL   — Set Process Color (15 bytes) — Device RGB
 *   GBAR     — Begin Area (2 bytes)
 *   GSCOL    — Set Color (outline, 2 bytes)
 *   GBOX     — Box compact form (12 bytes)
 *   GEAR     — End Area (2 bytes)
 * </pre>
 *
 * <p>Coordinates are flipped from AFP page-space (Y-down, origin at top)
 * to GOCA graphics-space (Y-up, origin at bottom) using page height.
 *
 * <p>GOCA drawing orders are built as raw bytes because afplib's Triplet
 * classes (GSPCOL, GBAR, GBOX, etc.) are not SF subclasses and cannot
 * be written through AfpOutputStream.
 */
public class GocaStreamBuilder {

    private static final int FILL_R = 240;
    private static final int FILL_G = 240;
    private static final int FILL_B = 240;

    private static final byte[] PROLOG = {
        0x70, 0x0C,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x27,
        0x00, 0x00, 0x00, 0x00,
        0x00,
        0x19, 0x00,
        0x18, 0x07,
        0x28, 0x10
    };

    private static final byte[] GSPCOL = {
        (byte)0xB2, 0x0D,
        0x00,
        0x01,
        0x00, 0x00, 0x00, 0x00,
        0x08, 0x08, 0x08, 0x00,
        (byte) FILL_R, (byte) FILL_G, (byte) FILL_B
    };

    private static final byte[] GBAR  = { 0x68, (byte)0x80 };
    private static final byte[] GSCOL = { 0x0A, 0x00 };
    private static final byte[] GEAR  = { 0x60, 0x00 };
    private static final byte[] GBOX_PREFIX = { (byte)0xC0, 0x0A, 0x00, 0x20 };
    private static final int GBOX_COORDS_BYTES = 8;

    /**
     * Builds the GOCA byte stream.
     *
     * @param originX    X position on the page (AFP coordinates)
     * @param originY    Y position on the page (AFP coordinates)
     * @param width      box width
     * @param height     box height (typically the ICP fill height)
     * @param pageHeight page height, used for Y-flip: gocaY = pageH - afpY - h
     */
    public byte[] build(int originX, int originY, int width,
                        int height, int pageHeight) {
        int gocaY0 = pageHeight - (originY + height);
        int gocaY1 = pageHeight - originY;

        int total = PROLOG.length + GSPCOL.length + GBAR.length
                  + GSCOL.length + GBOX_PREFIX.length + GBOX_COORDS_BYTES + GEAR.length;
        byte[] result = new byte[total];
        int off = 0;

        off = copyInto(result, off, PROLOG);
        off = copyInto(result, off, GSPCOL);
        off = copyInto(result, off, GBAR);
        off = copyInto(result, off, GSCOL);
        off = copyInto(result, off, GBOX_PREFIX);

        off = write16be(result, off, originX);
        off = write16be(result, off, gocaY0);
        off = write16be(result, off, originX + width);
        off = write16be(result, off, gocaY1);

        off = copyInto(result, off, GEAR);
        return result;
    }
}

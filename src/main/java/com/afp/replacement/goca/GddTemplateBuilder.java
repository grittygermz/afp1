package com.afp.replacement.goca;

import com.afp.replacement.model.PageInfo;
import static com.afp.replacement.util.ByteWriter.*;

/**
 * Builds the GDD (Graphics Data Descriptor) structured-field bytes
 * from PGD page parameters.
 *
 * <p>The GDD contains:
 * <ul>
 *   <li>A fixed descriptor block (taken from the papy reference)</li>
 *   <li>Measurement units ({@code xUnits, yUnits})</li>
 *   <li>Coordinate-space size ({@code xSize, ySize})</li>
 * </ul>
 *
 * <p>The field length is fixed at 0x29 (41).
 */
public class GddTemplateBuilder {

    private static final int LENGTH_FIELD = 0x29;
    private static final int SF_ID_GDD    = 0xD3A6BB;

    public static byte[] build(PageInfo page) {
        byte[] tmpl = new byte[LENGTH_FIELD + 1];
        int off = 0;

        tmpl[off++] = 0x5A;
        off = write16be(tmpl, off, LENGTH_FIELD);
        off = write24be(tmpl, off, SF_ID_GDD);
        off = write24be(tmpl, off, 0);

        // Fixed descriptor block (from papy reference)
        byte[] block = {
            (byte)0xF7, 0x07, (byte)0xB0, 0x00, 0x00,
            0x02, 0x00, 0x01,
            0x00, (byte)0xF6, 0x16,
            0x00, 0x00, 0x00, 0x00
        };
        off = copyInto(tmpl, off, block);

        // Measurement units (2 bytes each)
        off = write16be(tmpl, off, page.xUnits);
        off = write16be(tmpl, off, page.yUnits);

        // Reserved
        off = copyInto(tmpl, off, new byte[4]);

        // Size block: xSize(2) + gap(2) + ySize(2) + padding(4)
        off = write16be(tmpl, off, page.xSize);
        off = write16be(tmpl, off, 0);
        off = write16be(tmpl, off, page.ySize);
        off = copyInto(tmpl, off, new byte[4]);

        return tmpl;
    }
}

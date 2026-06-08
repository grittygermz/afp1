package com.afp.replacement.goca;

import com.afp.replacement.model.PageInfo;
import static com.afp.replacement.util.ByteWriter.*;

/**
 * Builds the OBD (Object Area Descriptor) structured-field bytes
 * from PGD page parameters.
 *
 * <p>The OBD payload contains three triplets:
 * <ul>
 *   <li>0x43 — reserved / colour management ({@code 03 43 01})</li>
 *   <li>0x4B — Measurement Units ({@code xUnits, yUnits})</li>
 *   <li>0x4C — Object Area Size ({@code xSize} as 2 bytes, {@code ySize} as 3 bytes)</li>
 * </ul>
 *
 * <p>The field length is fixed at 0x1C (28), matching the papy reference.
 */
public class ObdTemplateBuilder {

    private static final int LENGTH_FIELD = 0x1C;
    private static final int SF_ID_OBD    = 0xD3A66B;

    public static byte[] build(PageInfo page) {
        byte[] tmpl = new byte[LENGTH_FIELD + 1];
        int off = 0;

        tmpl[off++] = 0x5A;
        off = write16be(tmpl, off, LENGTH_FIELD);
        off = write24be(tmpl, off, SF_ID_OBD);
        off = write24be(tmpl, off, 0);

        tmpl[off++] = 0x03; tmpl[off++] = 0x43; tmpl[off++] = 0x01;

        tmpl[off++] = 0x08; tmpl[off++] = 0x4B;
        tmpl[off++] = 0x00; tmpl[off++] = 0x00;
        off = write16be(tmpl, off, page.xUnits);
        off = write16be(tmpl, off, page.yUnits);

        tmpl[off++] = 0x09; tmpl[off++] = 0x4C;
        tmpl[off++] = 0x02; tmpl[off++] = 0x00;
        off = write16be(tmpl, off, page.xSize);
        off = write24be(tmpl, off, page.ySize);

        return tmpl;
    }
}

package com.afp.replacement.parser;

import com.afp.replacement.model.ImageStrip;
import com.afp.replacement.model.PageInfo;
import com.afp.replacement.model.SectionBounds;
import org.afplib.afplib.BII;
import org.afplib.afplib.EII;
import org.afplib.afplib.ICP;
import org.afplib.afplib.PGD;
import org.afplib.io.AfpInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an AFP file (as a raw byte array) and extracts the information
 * needed for the GOCA conversion: PGD page parameters, ICP strip positions,
 * and the byte offsets of the BII/EII structured fields for header/trailer
 * slicing.
 *
 * <p>afplib is used for parsing the structured fields; raw byte scanning
 * is used for BII/EII offset detection because afplib's getOffset() can
 * be inaccurate when reading from a ByteArrayInputStream.
 */
public final class AfpSectionParser {

    private static final byte AFP_MAGIC = 0x5A;

    private static final int SF_ID_BII = 0xD3A87B;
    private static final int SF_ID_EII = 0xD3A97B;

    private AfpSectionParser() {}

    /**
     * Parses the provided raw AFP bytes.
     *
     * @param data    the complete AFP file contents
     * @param strips  list to populate with ImageStrip objects
     * @param page    PageInfo to populate from PGD
     * @return        header/trailer SectionBounds
     */
    public static SectionBounds parse(byte[] data, List<ImageStrip> strips, PageInfo page)
            throws IOException {
        parseAfpFields(data, strips, page);
        return locateBiiEii(data);
    }

    // ----------------------------------------------------------------
    //  afplib-based field parsing
    // ----------------------------------------------------------------

    private static void parseAfpFields(byte[] data, List<ImageStrip> strips, PageInfo page)
            throws IOException {
        try (AfpInputStream in = new AfpInputStream(new ByteArrayInputStream(data))) {
            boolean insideImageObject = false;

            while (true) {
                org.afplib.base.SF sf;
                try {
                    sf = in.readStructuredField();
                } catch (Exception e) {
                    break;
                }
                if (sf == null) break;

                if (sf instanceof PGD) {
                    page.readFrom((PGD) sf);
                } else if (sf instanceof BII) {
                    insideImageObject = true;
                    strips.clear();
                } else if (sf instanceof EII) {
                    insideImageObject = false;
                } else if (insideImageObject && sf instanceof ICP) {
                    strips.add(ImageStrip.fromIcp((ICP) sf));
                }
            }
        }
    }

    // ----------------------------------------------------------------
    //  Raw byte scanning for BII/EII boundaries
    // ----------------------------------------------------------------

    private static SectionBounds locateBiiEii(byte[] data) {
        int headerEnd    = -1;
        int trailerStart = -1;
        int pos          = 0;

        while (pos < data.length - 10) {
            if (data[pos] != AFP_MAGIC) {
                pos++;
                continue;
            }
            int length = ((data[pos + 1] & 0xFF) << 8) | (data[pos + 2] & 0xFF);
            int sfId   = ((data[pos + 3] & 0xFF) << 16)
                       | ((data[pos + 4] & 0xFF) << 8)
                       |  (data[pos + 5] & 0xFF);

            if (sfId == SF_ID_BII && headerEnd < 0) {
                headerEnd = pos;
            }
            if (sfId == SF_ID_EII && headerEnd >= 0 && trailerStart < 0) {
                trailerStart = pos + length + 1;
            }
            pos += length + 1;
        }

        if (headerEnd < 0 || trailerStart < 0) {
            throw new IllegalArgumentException(
                "No BII/EII pair found — input may not be a valid IM-image AFP file");
        }
        return new SectionBounds(headerEnd, trailerStart);
    }
}

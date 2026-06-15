package com.afp.replacement.parser;

import com.afp.replacement.model.ImageStrip;
import com.afp.replacement.model.PageInfo;
import com.afp.replacement.model.SectionBounds;
import com.afp.replacement.model.SectionBounds.ImageBlock;

import org.afplib.afplib.BII;
import org.afplib.afplib.EII;
import org.afplib.afplib.ICP;
import org.afplib.afplib.IID;
import org.afplib.afplib.IOC;
import org.afplib.afplib.PGD;
import org.afplib.io.AfpInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an AFP file (as a raw byte array) and extracts the information
 * needed for the GOCA conversion: PGD page parameters, IID image
 * coordinate-system, IOC origin offset, ICP strip positions, and the
 * byte offsets of all BII/EII boundaries.
 *
 * <p>afplib is used for parsing the structured fields; raw byte scanning
 * is used for BII/EII offset detection because afplib's getOffset() can
 * be inaccurate when reading from a ByteArrayInputStream.
 */
public final class AfpSectionParser {

    private static final byte AFP_MAGIC = 0x5A;
    private static final int SF_ID_BII  = 0xD3A87B;
    private static final int SF_ID_EII  = 0xD3A97B;
    private static final int SF_ID_ICP  = 0xD3AC7B;
    private static final int SF_ID_EAG  = 0xD3A9C9;

    private AfpSectionParser() {}

    /**
     * Parses the provided raw AFP bytes.
     *
     * @param data    the complete AFP file contents
     * @param page    PageInfo to populate from PGD, IOC, and IID
     * @return        SectionBounds with all image blocks and their strips
     */
    public static SectionBounds parse(byte[] data, PageInfo page)
            throws IOException {
        SectionBounds bounds = locateBiiEii(data);
        parseAfpFields(data, page, bounds.imageBlocks);
        page.validate();
        return bounds;
    }

    // ----------------------------------------------------------------
    //  afplib-based field parsing
    // ----------------------------------------------------------------

    private static void parseAfpFields(byte[] data, PageInfo page,
                                       List<ImageBlock> blocks) throws IOException {
        try (AfpInputStream in = new AfpInputStream(new ByteArrayInputStream(data))) {
            int blockIndex = -1;
            boolean iidSeen = false;

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
                    blockIndex++;
                    iidSeen = false;
                } else if (sf instanceof EII) {
                    // End of current block — nothing to do
                } else if (sf instanceof IOC && blockIndex >= 0
                           && blockIndex < blocks.size()) {
                    // Store the IOC offset on the block so each strip
                    // uses its own block's IOC, not a global value.
                    IOC ioc = (IOC) sf;
                    ImageBlock blk = blocks.get(blockIndex);
                    blk.xOffset = ioc.getXoaOset() != null ? ioc.getXoaOset() : 0;
                    blk.yOffset = ioc.getYoaOset() != null ? ioc.getYoaOset() : 0;
                    blk.iocSet = true;
                    // Also set global for single-block files
                    page.setIocOffset(ioc);
                } else if (sf instanceof IID && blockIndex >= 0) {
                    // IID defines the image's coordinate system (units).
                    // ICP coordinates/fill sizes are in this space and
                    // must be scaled to the page coordinate system (PGD).
                    // Only the FIRST IID sets the scale; subsequent IIDs
                    // (from later image blocks) are ignored.
                    page.setIidScale((IID) sf);
                    iidSeen = true;
                } else if (sf instanceof ICP && blockIndex >= 0) {
                    if (!iidSeen) {
                        throw new IOException(
                            "ICP encountered before IID — cannot determine image coordinate system");
                    }
                    if (blockIndex < blocks.size()) {
                        blocks.get(blockIndex).strips.add(ImageStrip.fromIcp((ICP) sf));
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------
    //  Raw byte scanning for all BII/EII pairs
    // ----------------------------------------------------------------

    private static SectionBounds locateBiiEii(byte[] data) {
        List<ImageBlock> blocks = new ArrayList<>();
        int headerEnd    = -1;
        int trailerStart = -1;
        int frontEnd     = -1;
        int pos          = 0;
        int biiStart     = -1;

        while (pos < data.length - 10) {
            if (data[pos] != AFP_MAGIC) {
                pos++;
                continue;
            }
            int length = ((data[pos + 1] & 0xFF) << 8) | (data[pos + 2] & 0xFF);
            int sfId   = ((data[pos + 3] & 0xFF) << 16)
                       | ((data[pos + 4] & 0xFF) << 8)
                       |  (data[pos + 5] & 0xFF);

            if (sfId == SF_ID_BII) {
                if (headerEnd < 0) {
                    headerEnd = pos;
                    if (frontEnd < 0) frontEnd = pos; // no EAG found
                }
                biiStart = pos;
            }

            if (sfId == SF_ID_EAG && frontEnd < 0) {
                // Record the byte right after the EAG so GOCA groups can
                // be inserted before the grid lines (which follow EAG).
                frontEnd = pos + length + 1;
            }

            if (sfId == SF_ID_EII && biiStart >= 0) {
                int blockEnd = pos + length + 1;
                boolean hasICP = hasIcpInRange(data, biiStart, blockEnd);
                blocks.add(new ImageBlock(biiStart, blockEnd, hasICP));
                biiStart = -1;
                trailerStart = blockEnd;  // update trailer to after this block
            }

            pos += length + 1;
        }

        if (headerEnd < 0 || trailerStart < 0 || blocks.isEmpty()) {
            throw new IllegalArgumentException(
                "No BII/EII pair found — input may not be a valid IM-image AFP file");
        }
        return new SectionBounds(frontEnd, headerEnd, trailerStart, blocks);
    }

    /** Checks whether any ICP SF exists between start (inclusive) and end (exclusive). */
    private static boolean hasIcpInRange(byte[] data, int start, int end) {
        int pos = start;
        while (pos < end - 10) {
            if (data[pos] == AFP_MAGIC) {
                int len = ((data[pos + 1] & 0xFF) << 8) | (data[pos + 2] & 0xFF);
                int id  = ((data[pos + 3] & 0xFF) << 16)
                        | ((data[pos + 4] & 0xFF) << 8)
                        |  (data[pos + 5] & 0xFF);
                if (id == SF_ID_ICP) return true;
                pos += len + 1;
            } else {
                pos++;
            }
        }
        return false;
    }
}

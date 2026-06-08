package com.afp.replacement.goca;

import com.afp.replacement.model.ImageStrip;
import com.afp.replacement.model.PageInfo;
import com.afp.replacement.util.EbcdicEncoder;

import java.io.IOException;
import java.io.OutputStream;

import static com.afp.replacement.util.ByteWriter.*;

/**
 * Writes one complete GOCA graphics group into an output stream.
 *
 * <p>Each group consists of 9 structured fields in this exact order:
 * <pre>
 *   BGR  — Begin GRaphics
 *   BOC  — Begin Object Container
 *   OBD  — Object Area Descriptor   [PGD-dependent]
 *   IOC  — Image Output Control
 *   IID  — Image Input Descriptor
 *   GDD  — Graphics Data Descriptor [PGD-dependent]
 *   EOC  — End Object Container
 *   GAD  — Graphics Area Descriptor [contains the GOCA byte stream]
 *   EGR  — End GRaphics
 * </pre>
 *
 * <p>Fields with static content (BGR, BOC, IOC, IID, EOC, EGR) are
 * pre-built as byte-array templates.  OBD, GDD, and GAD are built
 * at runtime from the page parameters and strip data.
 */
public class GraphicsGroupWriter {

    private static final int NAME_FIELD_LENGTH = 8;
    private static final int SF_HEADER_SIZE    = 9;
    private static final int SEQUENCE_OFFSET   = 8;
    private static final int NAME_OFFSET       = 9;

    private static final byte AFP_MAGIC = 0x5A;

    //  SF IDs
    private static final int SF_ID_BGR = 0xD3A8BB;
    private static final int SF_ID_BOC = 0xD3A8C7;
    private static final int SF_ID_IOC = 0xD3AC6B;
    private static final int SF_ID_IID = 0xD3ABBB;
    private static final int SF_ID_EOC = 0xD3A9C7;
    private static final int SF_ID_GAD = 0xD3EEBB;
    private static final int SF_ID_EGR = 0xD3A9BB;

    private final byte[] obdTemplate;
    private final byte[] gddTemplate;
    private final GocaStreamBuilder gocaBuilder;
    private final int pageHeight;

    public GraphicsGroupWriter(PageInfo page) {
        this.obdTemplate = ObdTemplateBuilder.build(page);
        this.gddTemplate = GddTemplateBuilder.build(page);
        this.gocaBuilder = new GocaStreamBuilder();
        this.pageHeight  = page.ySize;
    }

    /** Writes one complete 9-field strip group.  seq is the starting sequence number. */
    public void write(OutputStream out, ImageStrip strip, int seq) throws IOException {
        byte[] name = EbcdicEncoder.encode("OGL GOCA", NAME_FIELD_LENGTH);

        writeTemplate(out, new byte[][]{
            staticTemplate(SF_ID_BGR, 0x10, name),
            staticTemplate(SF_ID_BOC, 0x10, name),
            obdTemplate,
            staticTemplate(SF_ID_IOC, 0x20, null),
            staticTemplate(SF_ID_IID, 0x0D, null),
            gddTemplate,
            staticTemplate(SF_ID_EOC, 0x10, name),
        }, seq);

        writeGadSf(out, strip, seq + 7);

        writeTemplate(out, new byte[][]{
            staticTemplate(SF_ID_EGR, 0x10, name),
        }, seq + 8);
    }

    // ----------------------------------------------------------------
    //  GAD — Graphics Area Descriptor
    // ----------------------------------------------------------------

    private void writeGadSf(OutputStream out, ImageStrip strip, int seq)
            throws IOException {
        // Use fill sizes (XFilSize/YFilSize) when available; these are
        // the actual rendered extents.  Fall back to cell sizes (XCSize/
        // YCSize) when fill is zero, which happens when it is absent.
        int boxWidth  = (strip.fillWidth  > 0) ? strip.fillWidth  : strip.cellWidth;
        int boxHeight = (strip.fillHeight > 0) ? strip.fillHeight : strip.cellHeight;
        byte[] gocaData = gocaBuilder.build(
            strip.originX, strip.originY,
            boxWidth, boxHeight, pageHeight);

        int totalLength = SF_HEADER_SIZE + gocaData.length;
        int lengthField = totalLength - 1;

        out.write(AFP_MAGIC);
        write16(out, lengthField);
        write24(out, SF_ID_GAD);
        write24(out, seq);
        out.write(gocaData);
    }

    // ----------------------------------------------------------------
    //  Template writing
    // ----------------------------------------------------------------

    /** Writes one or more template SFs, patching sequence numbers sequentially. */
    private void writeTemplate(OutputStream out, byte[][] templates, int baseSeq)
            throws IOException {
        for (int i = 0; i < templates.length; i++) {
            byte[] copy = templates[i].clone();
            copy[SEQUENCE_OFFSET] = (byte) ((baseSeq + i) & 0xFF);
            out.write(copy);
        }
    }

    // ----------------------------------------------------------------
    //  Template cache for constant SFs
    // ----------------------------------------------------------------

    private static final Object lock = new Object();
    private static java.util.Map<String, byte[]> templateCache = new java.util.HashMap<>();

    private static byte[] staticTemplate(int sfId, int lengthField, byte[] name) {
        String key = sfId + ":" + lengthField + ":" + java.util.Arrays.hashCode(name);
        synchronized (lock) {
            return templateCache.computeIfAbsent(key, k -> buildStaticSf(sfId, lengthField, name));
        }
    }

    private static byte[] buildStaticSf(int sfId, int lengthField, byte[] name) {
        int total = lengthField + 1;
        byte[] result = new byte[total];
        int off = 0;

        result[off++] = AFP_MAGIC;
        off = write16be(result, off, lengthField);
        off = write24be(result, off, sfId);
        off = write24be(result, off, 0);

        if (name != null) {
            off = copyInto(result, off, name);
        }
        return result;
    }
}

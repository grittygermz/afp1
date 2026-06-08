package com.afp.replacement.model;

import org.afplib.afplib.ICP;

/**
 * Represents one image strip as described by an ICP (Image Cell Position)
 * structured field.  The ICP defines the cell's origin and size, plus the
 * fill extent — the actual rendered area on the page.
 */
public class ImageStrip {

    /** X origin of the cell (XCOset). */
    public final int originX;
    /** Y origin of the cell (YCOset). */
    public final int originY;
    /** Cell width (XCSize). */
    public final int cellWidth;
    /** Cell height (YCSize) — this is the per-band tile height. */
    public final int cellHeight;
    /** Fill width (XFilSize) — actual rendered width. */
    public final int fillWidth;
    /** Fill height (YFilSize) — actual rendered height. */
    public final int fillHeight;

    public ImageStrip(int originX, int originY, int cellWidth, int cellHeight,
                      int fillWidth, int fillHeight) {
        this.originX = originX;
        this.originY = originY;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.fillWidth = fillWidth;
        this.fillHeight = fillHeight;
    }

    /** Convenience factory that reads from an afplib ICP object. */
    public static ImageStrip fromIcp(ICP icp) {
        return new ImageStrip(
            valueOrZero(icp.getXCOset()),
            valueOrZero(icp.getYCOset()),
            valueOrZero(icp.getXCSize()),
            valueOrZero(icp.getYCSize()),
            valueOrZero(icp.getXFilSize()),
            valueOrZero(icp.getYFilSize())
        );
    }

    private static int valueOrZero(Integer v) {
        return v != null ? v : 0;
    }
}

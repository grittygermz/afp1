package com.afp.replacement.model;

import org.afplib.afplib.PGD;

/**
 * Stores page-descriptor values needed for coordinate mapping between
 * AFP page coordinates (Y-down) and GOCA graphics coordinates (Y-up).
 *
 * <p>Default values correspond to a typical A5 page at 2400 Ln/in.
 * These are overwritten when a PGD field is present in the input file.
 */
public class PageInfo {

    public int xUnits = 2400;
    public int yUnits = 2400;
    public int xSize  = 1984;
    public int ySize  = 2806;

    /** Populates fields from an afplib PGD object where non-null. */
    public void readFrom(PGD pgd) {
        if (pgd.getXpgUnits() != null) this.xUnits = pgd.getXpgUnits();
        if (pgd.getYpgUnits() != null) this.yUnits = pgd.getYpgUnits();
        if (pgd.getXpgSize()  != null) this.xSize  = pgd.getXpgSize();
        if (pgd.getYpgSize()  != null) this.ySize  = pgd.getYpgSize();
    }
}

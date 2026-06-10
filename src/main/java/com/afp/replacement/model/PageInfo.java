package com.afp.replacement.model;

import org.afplib.afplib.IOC;
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

    /**
     * Origin offset applied by the IOC (Image Output Control) field.
     * ICP coordinates are relative to this origin; GOCA GBOX coordinates
     * must be absolute page positions, so these offsets are added.
     */
    public int xOriginOffset = 0;
    public int yOriginOffset = 0;

    /** Populates fields from an afplib PGD object where non-null. */
    public void readFrom(PGD pgd) {
        if (pgd.getXpgUnits() != null) this.xUnits = pgd.getXpgUnits();
        if (pgd.getYpgUnits() != null) this.yUnits = pgd.getYpgUnits();
        if (pgd.getXpgSize()  != null) this.xSize  = pgd.getXpgSize();
        if (pgd.getYpgSize()  != null) this.ySize  = pgd.getYpgSize();
    }

    /** Populates origin offset from an afplib IOC object. */
    public void setIocOffset(IOC ioc) {
        if (ioc.getXoaOset() != null) this.xOriginOffset = ioc.getXoaOset();
        if (ioc.getYoaOset() != null) this.yOriginOffset = ioc.getYoaOset();
    }
}

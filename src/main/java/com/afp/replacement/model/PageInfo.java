package com.afp.replacement.model;

import org.afplib.afplib.IID;
import org.afplib.afplib.IOC;
import org.afplib.afplib.PGD;

/**
 * Stores page-descriptor values needed for coordinate mapping between
 * AFP page coordinates (Y-down) and GOCA graphics coordinates (Y-up).
 *
 * <p>Fields are populated from PGD, IOC, and IID structured fields in
 * the input file.  All fields must be set before use — the constructor
 * leaves them uninitialised and {@link #validate()} throws if any are
 * missing.
 */
public class PageInfo {

    public int xUnits;
    public int yUnits;
    public int xSize;
    public int ySize;
    public boolean pageSet;

    /**
     * Origin offset applied by the IOC (Image Output Control) field.
     * ICP coordinates are relative to this origin.
     */
    public int xOriginOffset;
    public int yOriginOffset;

    /**
     * Scale factor to convert ICP coordinates/fill sizes from the IID's
     * coordinate space to the page coordinate space.
     *
     * ICP values are in the image coordinate system defined by IID.
     * GOCA GBOX coordinates must be in the page coordinate system (PGD).
     * When IID units differ from PGD units, this factor is > 1.
     */
    public double scaleX = 1.0;
    public double scaleY = 1.0;
    private boolean iidScaleSet;

    /** Populates fields from an afplib PGD object. */
    public void readFrom(PGD pgd) {
        xUnits = require(pgd.getXpgUnits(), "PGD.xUnits");
        yUnits = require(pgd.getYpgUnits(), "PGD.yUnits");
        xSize  = require(pgd.getXpgSize(),  "PGD.xSize");
        ySize  = require(pgd.getYpgSize(),  "PGD.ySize");
        pageSet = true;
    }

    /** Populates origin offset from an afplib IOC object. */
    public void setIocOffset(IOC ioc) {
        xOriginOffset = require(ioc.getXoaOset(), "IOC.xoaOset");
        yOriginOffset = require(ioc.getYoaOset(), "IOC.yoaOset");
    }

    /**
     * Computes the IID-to-page scale factors.
     * Must be called after both {@link #readFrom(PGD)} and after the
     * IID field has been parsed.
     */
    /**
     * Computes the IID-to-page scale factors from the first IID encountered.
     * Subsequent calls are ignored so that later IID values (e.g. from a
     * second image block) don't corrupt the scaling established by the first
     * block's IID.
     */
    public void setIidScale(IID iid) {
        if (iidScaleSet) return;
        int iidXUnits = require(iid.getXUnits(), "IID.xUnits");
        int iidYUnits = require(iid.getYUnits(), "IID.yUnits");
        this.scaleX = (double) xUnits / iidXUnits;
        this.scaleY = (double) yUnits / iidYUnits;
        iidScaleSet = true;
    }

    /** Throws if any required page fields are missing. */
    public void validate() {
        if (!pageSet) {
            throw new IllegalStateException("No PGD found — cannot determine page coordinate system");
        }
    }

    private static int require(Integer v, String label) {
        if (v == null) {
            throw new IllegalArgumentException("Required field is null: " + label);
        }
        return v;
    }
}

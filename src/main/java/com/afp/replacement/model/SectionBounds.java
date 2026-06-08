package com.afp.replacement.model;

/**
 * Immutable byte-range boundaries for the header (before BII) and
 * trailer (after EII) sections of an AFP file.
 */
public class SectionBounds {

    /** Exclusive end of the header section (position of BII's 5A byte). */
    public final int headerEnd;
    /** Inclusive start of the trailer section (first byte after EII). */
    public final int trailerStart;

    public SectionBounds(int headerEnd, int trailerStart) {
        this.headerEnd = headerEnd;
        this.trailerStart = trailerStart;
    }
}

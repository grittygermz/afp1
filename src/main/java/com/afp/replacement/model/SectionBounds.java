package com.afp.replacement.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Byte-range boundaries for an AFP file containing image objects.
 * Header (before first BII), trailer (after last EII), and a list
 * of individual BII…EII image blocks, each of which may be either
 * ICP-based (replaceable with GOCA groups) or IRD-only (preserved
 * as raw bytes).
 */
public class SectionBounds {

    /**
     * Byte after the EAG (End Active Environment Group) SF.
     * GOCA groups are inserted here so they appear behind the grid
     * lines (BPT/PTX/EPT) that follow.  When no EAG is present in
     * the header, this equals {@link #headerEnd}.
     */
    public final int frontEnd;

    /** Exclusive end of the header (byte offset of the first BII's 5A). */
    public final int headerEnd;
    /** Inclusive start of the trailer (first byte after the last EII). */
    public final int trailerStart;

    /** One entry per BII…EII pair in the file, in file order. */
    public final List<ImageBlock> imageBlocks;

    public SectionBounds(int frontEnd, int headerEnd, int trailerStart,
                         List<ImageBlock> imageBlocks) {
        this.frontEnd = frontEnd;
        this.headerEnd = headerEnd;
        this.trailerStart = trailerStart;
        this.imageBlocks = imageBlocks;
    }

    /**
     * Describes one BII…EII block.  When {@code hasICP} is true the
     * block contains ICP image cells that can be replaced with GOCA
     * vector boxes.  When false it is an IRD-only block whose raw
     * bytes must be preserved as-is.
     */
    public static class ImageBlock {
        /** Raw file offset of the BII's 5A byte. */
        public final int start;
        /** Raw file offset of the first byte after the EII. */
        public final int end;
        /** Whether this block contains ICP entries. */
        public final boolean hasICP;
        /** Strips belonging to this block (populated during afplib parsing). */
        public final List<ImageStrip> strips = new ArrayList<>();

        public ImageBlock(int start, int end, boolean hasICP) {
            this.start = start;
            this.end = end;
            this.hasICP = hasICP;
        }
    }
}

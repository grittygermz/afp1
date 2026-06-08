package com.afp.replacement;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.afp.replacement.goca.GraphicsGroupWriter;
import com.afp.replacement.model.ImageStrip;
import com.afp.replacement.model.PageInfo;
import com.afp.replacement.model.SectionBounds;
import com.afp.replacement.parser.AfpSectionParser;

/**
 * Replaces legacy IM (Image) raster data in AFP files with GOCA vector
 * filled rectangles.
 *
 * <p>The program reads an AFP file containing an IM image object (BII…EII
 * block with ICP/IRD strips), strips it out, and injects one GOCA graphics
 * group per strip.  Each group draws a filled grey rectangle at the same
 * position and extent as the original raster tile.
 *
 * <p>Usage:
 * <pre>  java com.afp.replacement.AfpColorReplacer [input.afp] [output.afp]</pre>
 *
 * <h3>Architecture note</h3>
 * afplib is used <em>only for reading/parsing</em> (PGD, ICP, BII/EII
 * detection).  The output is written as raw bytes because the structured
 * fields we emit (OBD, IOC, IID, GDD) are not fully handled by afplib's
 * binary() serialiser — they become UNKNSF objects whose stored raw data
 * causes a duplicated 5A magic byte when re-written via AfpOutputStream.
 */
public class AfpColorReplacer {

    public static void main(String[] args) throws IOException {
        String input  = "O1XB3131_original.afp";
        String output = "O1XB3131_output.afp";
        // String input  = "O1XB3130_original.afp";
        // String output = "O1XB3130_output.afp";
        // String input  = "O1XB0024_original.afp";
        // String output = "O1XB0024_output.afp";

        if (args.length >= 2) {
            input  = args[0];
            output = args[1];
        } else if (args.length == 1) {
            input = args[0];
        }

        System.out.println("Input: " + input + "\nOutput: " + output);
        convert(input, output);
        System.out.println("Conversion complete!");
    }

    // ----------------------------------------------------------------
    //  Orchestrator
    // ----------------------------------------------------------------

    /**
     * Performs the full conversion:
     * <ol>
     *   <li>Read the input file into memory</li>
     *   <li>Parse it with afplib (PGD page parameters, ICP strip positions)</li>
     *   <li>Locate BII/EII boundaries via raw byte scanning</li>
     *   <li>Emit the header bytes unchanged</li>
     *   <li>Emit one GOCA graphics group per strip</li>
     *   <li>Emit the trailer bytes unchanged</li>
     * </ol>
     */
    public static void convert(String inputPath, String outputPath) throws IOException {
        // ---- Read ----
        byte[] fullInput;
        try (InputStream in = new FileInputStream(inputPath)) {
            fullInput = in.readAllBytes();
        }

        // ---- Parse ----
        List<ImageStrip> strips = new ArrayList<>();
        PageInfo page           = new PageInfo();
        SectionBounds bounds    = AfpSectionParser.parse(fullInput, strips, page);

        // ---- Slice header and trailer from raw bytes ----
        byte[] header  = java.util.Arrays.copyOfRange(fullInput, 0,             bounds.headerEnd);
        byte[] trailer = java.util.Arrays.copyOfRange(fullInput, bounds.trailerStart, fullInput.length);

        logSummary(strips, page, header, trailer);

        // ---- Write ----
        GraphicsGroupWriter groupWriter = new GraphicsGroupWriter(page);

        try (OutputStream out = new FileOutputStream(outputPath)) {
            out.write(header);

            // Sequence numbers run from 0 up to 9 per strip × number of strips.
            // The viewer only requires uniqueness, not specific values.
            int sequenceNumber = 0;
            for (ImageStrip strip : strips) {
                groupWriter.write(out, strip, sequenceNumber);
                sequenceNumber += 9;
            }

            out.write(trailer);
        }
    }

    // ----------------------------------------------------------------
    //  Logging
    // ----------------------------------------------------------------

    private static void logSummary(List<ImageStrip> strips, PageInfo page,
                                   byte[] header, byte[] trailer) {
        System.out.println("  Strips=" + strips.size()
            + "  page=" + page.xSize + "\u00d7" + page.ySize
            + "  units=" + page.xUnits + "\u00d7" + page.yUnits
            + "  header=" + header.length + "  trailer=" + trailer.length);
    }
}

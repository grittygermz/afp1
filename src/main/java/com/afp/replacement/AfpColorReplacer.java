package com.afp.replacement;

import com.afp.replacement.goca.GraphicsGroupWriter;
import com.afp.replacement.model.ImageStrip;
import com.afp.replacement.model.PageInfo;
import com.afp.replacement.model.SectionBounds;
import com.afp.replacement.model.SectionBounds.ImageBlock;
import com.afp.replacement.parser.AfpSectionParser;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Batch AFP-to-GOCA converter.
 *
 * <p>Reads every file from an {@code in/} folder next to the jar, converts
 * those that contain IM image objects (BII…EII blocks with ICP/IRD strips),
 * and writes GOCA-graphics replacements to an {@code output/} folder.
 *
 * <p>Logging goes to console and to a timestamped file inside {@code log/}.
 * A tracking CSV in {@code output/} records converted and skipped files.
 *
 * <p>Usage: {@code java -jar afp-converter.jar}
 */
public class AfpColorReplacer {

    /** Base directory = where the jar sits. */
    private static Path jarDir;

    /** Input folder = {@code <jarDir>/in/}. */
    private static Path inputDir;
    /** Output folder = {@code <jarDir>/output/}. */
    private static Path outputDir;
    /** Log folder = {@code <jarDir>/log/}. */
    private static Path logDir;
    /** Tracking CSV (in log/ with timestamp). */
    private static Path trackingFile;
    private static String runTimestamp;

    private static int convertedCount = 0;
    private static int skippedCount   = 0;
    private static PrintWriter logWriter;

    // ================================================================
    //  Entry point
    // ================================================================

    public static void main(String[] args) {
        try {
            resolveDirectories();
            createDirectories();
            initializeLogging();
            log("=== AFP GOCA Converter started at %s ===%n", timestamp());
            log("Jar directory: %s%n", jarDir);

            // List files in the input directory
            File[] files = inputDir.toFile().listFiles();
            if (files == null || files.length == 0) {
                log("No files found in '%s' — nothing to do.%n", inputDir);
                logWriter.close();
                return;
            }
            log("Found %d file(s) to process.%n", files.length);

            // Tracking CSV
            try (PrintWriter track = new PrintWriter(new FileWriter(trackingFile.toFile()))) {
                track.println("filename,status,strips,page_size,reason");

                for (File f : files) {
                    if (f.isFile()) {
                        processOneFile(f.toPath(), track);
                    }
                }
            }

            log("%n=== Summary: %d converted, %d skipped ===%n", convertedCount, skippedCount);
            log("Tracking: %s%n", trackingFile);
            log("Log file: %s%n", getCurrentLogPath());
            log("Output:   %s%n", outputDir);

        } catch (Exception e) {
            System.err.println("FATAL: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (logWriter != null) logWriter.close();
        }
    }

    // ================================================================
    //  Directory resolution (relative to the jar)
    // ================================================================

    /**
     * Determines the jar's location at runtime so that the {@code in/},
     * {@code output/} and {@code log/} directories are found relative to
     * the jar regardless of the current working directory.
     */
    private static void resolveDirectories() {
        try {
            String jarPath = AfpColorReplacer.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath();
            // On Windows the path may start with /C:/… ; decode URL encoding
            jarPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8);
            if (jarPath.startsWith("/") && jarPath.length() > 3 && jarPath.charAt(2) == ':') {
                jarPath = jarPath.substring(1);
            }
            jarDir = Paths.get(jarPath).getParent();
        } catch (Exception e) {
            // Fallback to working directory
            jarDir = Paths.get(".").normalize().toAbsolutePath();
        }

        // Strip any leading slash on Windows drives
        inputDir     = jarDir.resolve("in");
        outputDir    = jarDir.resolve("output");
        logDir       = jarDir.resolve("log");
        runTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        trackingFile = logDir.resolve("conversion_log_" + runTimestamp + ".csv");
    }

    private static void createDirectories() throws IOException {
        Files.createDirectories(outputDir);
        Files.createDirectories(logDir);
        // inputDir is expected to exist already; if not, we handle it gracefully
    }

    // ================================================================
    //  Logging
    // ================================================================

    private static void initializeLogging() throws IOException {
        Path logFile = getCurrentLogPath();
        logWriter = new PrintWriter(new FileWriter(logFile.toFile(), true), true);
    }

    private static Path getCurrentLogPath() {
        return logDir.resolve("conversion_" + runTimestamp + ".log");
    }

    private static void log(String format, Object... args) {
        String msg = String.format(format, args);
        System.out.print(msg);
        if (logWriter != null) {
            logWriter.print(msg);
            logWriter.flush();
        }
    }

    private static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ================================================================
    //  Per-file processing
    // ================================================================

    private static void processOneFile(Path inputPath, PrintWriter track) {
        String fileName = inputPath.getFileName().toString();
        Path outputPath = outputDir.resolve(fileName);

        log("--- %s%n", fileName);

        try {
            byte[] inputData = Files.readAllBytes(inputPath);
            PageInfo page = new PageInfo();

            SectionBounds bounds;
            try {
                bounds = AfpSectionParser.parse(inputData, page);
            } catch (IllegalArgumentException e) {
                log("  SKIPPED: %s%n", e.getMessage());
                Files.copy(inputPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
                log("  -> %s (copied as-is)%n", outputPath.getFileName());
                track.printf("%s,skipped,0,0x0,%s%n", fileName, e.getMessage());
                skippedCount++;
                return;
            }

            // Slice front (up to EAG), grid (EAG to first BII), and trailer
            byte[] front   = copyOfRange(inputData, 0,              bounds.frontEnd);
            byte[] grid    = copyOfRange(inputData, bounds.frontEnd, bounds.headerEnd);
            byte[] trailer = copyOfRange(inputData, bounds.trailerStart, inputData.length);

            // Count total strips across all ICP-containing blocks
            int totalStrips = bounds.imageBlocks.stream()
                .filter(b -> b.hasICP)
                .mapToInt(b -> b.strips.size()).sum();

            log("  Strips=%d  page=%dx%d  units=%dx%d  front=%d  grid=%d  trailer=%d  blocks=%d%n",
                totalStrips, page.xSize, page.ySize, page.xUnits, page.yUnits,
                front.length, grid.length, trailer.length, bounds.imageBlocks.size());

            // Write output.
            // Order: front (thru EAG) → GOCA groups → grid (BPT/PTX/EPT) →
            //        non-ICP blocks (IRD-only) → trailer.
            // GOCA groups come before the grid lines so the grid paints on top.
            GraphicsGroupWriter groupWriter = new GraphicsGroupWriter(page);
            try (OutputStream out = new FileOutputStream(outputPath.toFile())) {
                out.write(front);

                // Emit all GOCA groups from ICP-containing blocks.
                // Each block may have its own IOC offset; pass it per-strip.
                int seq = 0;
                for (ImageBlock block : bounds.imageBlocks) {
                    if (block.hasICP) {
                        int bx = block.iocSet ? block.xOffset : page.xOriginOffset;
                        int by = block.iocSet ? block.yOffset : page.yOriginOffset;
                        for (ImageStrip strip : block.strips) {
                            groupWriter.write(out, strip, seq, bx, by);
                            seq += 9;
                        }
                    }
                }

                out.write(grid);

                // Preserve non-ICP (IRD-only) blocks as raw bytes
                for (ImageBlock block : bounds.imageBlocks) {
                    if (!block.hasICP) {
                        out.write(inputData, block.start, block.end - block.start);
                    }
                }

                out.write(trailer);
            }

            log("  -> %s (%d bytes)%n", outputPath.getFileName(), Files.size(outputPath));
            track.printf("%s,converted,%d,%dx%d,%n",
                fileName, totalStrips, page.xSize, page.ySize);
            convertedCount++;

        } catch (Exception e) {
            log("  ERROR: %s%n", e.getMessage());
            track.printf("%s,error,0,0x0,%s%n", fileName, e.getMessage());
            skippedCount++;
        }
    }

    // ================================================================
    //  Utility
    // ================================================================

    private static byte[] copyOfRange(byte[] src, int from, int to) {
        return java.util.Arrays.copyOfRange(src, from, to);
    }
}

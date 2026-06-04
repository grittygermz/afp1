package com.afp.replacement;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class AfpColorReplacer {

    public static void main(String[] args) throws IOException {
        String inputFile = "O1XB3131_original.afp";
        String outputFile = "O1XB3131_output.afp";

        if (args.length >= 1) inputFile = args[0];
        if (args.length >= 2) outputFile = args[1];

        System.out.println("Input:  " + inputFile);
        System.out.println("Output: " + outputFile);

        replace(inputFile, outputFile);

        System.out.println("Done. Output size: " + new File(outputFile).length() + " bytes");
    }

    public static void replace(String inputFile, String outputFile) throws IOException {
        byte[] fileBytes = readFile(inputFile);

        List<Tile> tiles = new ArrayList<>();
        int pageHeight = 0;

        int fileLen = fileBytes.length;
        int imageStart = -1;
        int imageEnd = -1;
        int pos = 0;
        boolean foundImage = false;
        boolean inImage = false;

        while (pos < fileLen) {
            if (fileBytes[pos] != (byte) 0x5A) {
                pos++;
                continue;
            }
            if (pos + 8 >= fileLen) break;

            int sfLen = ((fileBytes[pos + 1] & 0xFF) << 8) | (fileBytes[pos + 2] & 0xFF);
            int sfId = ((fileBytes[pos + 3] & 0xFF) << 16)
                     | ((fileBytes[pos + 4] & 0xFF) << 8)
                     | (fileBytes[pos + 5] & 0xFF);
            int sfTotal = 1 + sfLen;

            if (!inImage && sfId == 0xD3A87B) { // BII
                foundImage = true;
                inImage = true;
                imageStart = pos;
                pos += sfTotal;
                continue;
            }
            if (inImage && sfId == 0xD3A97B) { // EII
                inImage = false;
                imageEnd = pos + sfTotal;
                pos += sfTotal;
                continue;
            }
            if (sfId == 0xD3A6AF) { // PGD
                if (pos + 21 < fileLen) {
                    int ySize = ((fileBytes[pos + 18] & 0xFF) << 16)
                              | ((fileBytes[pos + 19] & 0xFF) << 8)
                              | (fileBytes[pos + 20] & 0xFF);
                    pageHeight = ySize;
                }
            }
            if (inImage && sfId == 0xD3AC7B) { // ICP
                if (pos + 19 < fileLen) {
                    int x = ((fileBytes[pos + 9] & 0xFF) << 8) | (fileBytes[pos + 10] & 0xFF);
                    int y = ((fileBytes[pos + 11] & 0xFF) << 8) | (fileBytes[pos + 12] & 0xFF);
                    int w = ((fileBytes[pos + 13] & 0xFF) << 8) | (fileBytes[pos + 14] & 0xFF);
                    int h = ((fileBytes[pos + 15] & 0xFF) << 8) | (fileBytes[pos + 16] & 0xFF);
                    tiles.add(new Tile(x, y, w, h));
                }
            }
            pos += sfTotal;
        }

        if (imageStart < 0 || imageEnd < 0 || !foundImage) {
            System.err.println("No IM image found in file");
            return;
        }

        System.out.printf("Found %d image tiles, pageHeight=%d%n", tiles.size(), pageHeight);

        byte[] preImage = new byte[imageStart];
        System.arraycopy(fileBytes, 0, preImage, 0, imageStart);

        byte[] postImage = new byte[fileBytes.length - imageEnd];
        System.arraycopy(fileBytes, imageEnd, postImage, 0, postImage.length);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(preImage);

            int tileIdx = 0;
            for (Tile t : tiles) {
                tileIdx++;
                String name = String.format("GOCAB%02d", tileIdx);
                String areaName = String.format("AREAB%02d", tileIdx);

                byte[] goca = buildGocaDrawingOrders(t.x, t.y, t.w, t.h, pageHeight);

                byte[] bgrBytes = buildRawBGR(name);
                byte[] bocBytes = buildRawBOC(areaName, goca);
                byte[] egrBytes = buildRawEGR();

                fos.write(bgrBytes);
                fos.write(bocBytes);
                fos.write(egrBytes);

                System.out.printf("  Tile %2d: x=%4d y=%3d w=%2d h=%2d -> GOCA GOCAB%02d%n",
                    tileIdx, t.x, t.y, t.w, t.h, tileIdx);
            }

            fos.write(postImage);
        }
    }

    private static byte[] buildRawBGR(String name) throws IOException {
        byte[] nameData = pad8(name.getBytes("IBM500"));
        return buildRawSF(0xD3A8BB, nameData);
    }

    private static byte[] buildRawBOC(String name, byte[] gocaData) throws IOException {
        byte[] nameBytes = pad8(name.getBytes("IBM500"));
        byte[] data = new byte[nameBytes.length + gocaData.length];
        System.arraycopy(nameBytes, 0, data, 0, nameBytes.length);
        System.arraycopy(gocaData, 0, data, nameBytes.length, gocaData.length);
        return buildRawSF(0xD3A892, data);
    }

    private static byte[] buildRawEGR() throws IOException {
        return buildRawSF(0xD3A9BB, new byte[0]);
    }

    private static byte[] buildRawSF(int sfId, byte[] data) {
        int totalLen = 9 + data.length;
        byte[] sf = new byte[totalLen];
        sf[0] = (byte) 0x5A;
        int lenField = totalLen - 1;
        sf[1] = (byte) ((lenField >> 8) & 0xFF);
        sf[2] = (byte) (lenField & 0xFF);
        sf[3] = (byte) ((sfId >> 16) & 0xFF);
        sf[4] = (byte) ((sfId >> 8) & 0xFF);
        sf[5] = (byte) (sfId & 0xFF);
        sf[6] = 0x00;
        sf[7] = 0x00;
        sf[8] = 0x00;
        if (data.length > 0) {
            System.arraycopy(data, 0, sf, 9, data.length);
        }
        return sf;
    }

    private static byte[] pad8(byte[] input) {
        byte[] result = new byte[8];
        int copyLen = Math.min(input.length, 8);
        System.arraycopy(input, 0, result, 0, copyLen);
        for (int i = copyLen; i < 8; i++) result[i] = 0x40;
        return result;
    }

    private static byte[] buildGocaDrawingOrders(int x, int y, int w, int h, int pageHeight) {
        int gocaY0 = pageHeight > 0 ? pageHeight - (y + h) : y;
        int gocaY1 = pageHeight > 0 ? pageHeight - y : y + h;
        int x1 = x + w;

        byte[] gsec = {
            (byte) 0x10, 0x06,
            0x00, 0x08,
            0x00,
            (byte) 0xF0, (byte) 0xF0, (byte) 0xF0
        };

        byte[] gbox = {
            (byte) 0x08, 0x0A,
            (byte) ((x >> 8) & 0xFF), (byte) (x & 0xFF),
            (byte) ((gocaY0 >> 8) & 0xFF), (byte) (gocaY0 & 0xFF),
            (byte) ((x1 >> 8) & 0xFF), (byte) (x1 & 0xFF),
            (byte) ((gocaY1 >> 8) & 0xFF), (byte) (gocaY1 & 0xFF),
            0x00, 0x00
        };

        byte[] result = new byte[gsec.length + gbox.length];
        System.arraycopy(gsec, 0, result, 0, gsec.length);
        System.arraycopy(gbox, 0, result, gsec.length, gbox.length);
        return result;
    }

    private static byte[] readFile(String path) throws IOException {
        try (FileInputStream fis = new FileInputStream(path)) {
            return fis.readAllBytes();
        }
    }

    private static class Tile {
        final int x, y, w, h;
        Tile(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }
}

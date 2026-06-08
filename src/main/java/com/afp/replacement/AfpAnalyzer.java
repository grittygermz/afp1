package com.afp.replacement;

import org.afplib.base.SF;
import org.afplib.io.AfpInputStream;
import org.afplib.afplib.*;

import java.io.FileInputStream;
import java.io.IOException;

public class AfpAnalyzer {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: AfpAnalyzer <file.afp>");
            return;
        }
        analyzeFile(args[0]);
    }

    static void analyzeFile(String path) throws IOException {
        System.out.println("=== Analyzing: " + path + " ===");
        try (AfpInputStream in = new AfpInputStream(new FileInputStream(path))) {
            SF sf;
            int count = 0;
            int pageCount = 0;
            boolean inImage = false;
            boolean inGraphics = false;
            boolean inPage = false;
            boolean inDoc = false;

            while ((sf = sf = in.readStructuredField()) != null) {
                String name = sf.getClass().getSimpleName().replace("Impl", "");
                String details = "";

                if (sf instanceof BPG) {
                    pageCount++;
                    inPage = true;
                    details = " page=" + ((BPG) sf).getPageName();
                } else if (sf instanceof EPG) { inPage = false;
                } else if (sf instanceof BDT) { inDoc = true; details = " docName=" + ((BDT) sf).getDocName();
                } else if (sf instanceof EDT) { inDoc = false;
                } else if (sf instanceof BIM) {
                    inImage = true;
                    details = " idoName=" + ((BIM) sf).getIdoName();
                } else if (sf instanceof EIM) {
                    inImage = false;
                    details = " idoName=" + ((EIM) sf).getIdoName();
                } else if (sf instanceof BOG) {
                    inGraphics = true;
                    details = " oegName=" + ((BOG) sf).getOEGName();
                } else if (sf instanceof EOG) {
                    inGraphics = false;
                    details = " oegName=" + ((EOG) sf).getOEGName();
                } else if (sf instanceof ICP) {
                    ICP icp = (ICP) sf;
                    details = String.format(" X=%d Y=%d W=%d H=%d",
                            icp.getXCOset(), icp.getYCOset(), icp.getXCSize(), icp.getYCSize());
                } else if (sf instanceof IOC) {
                    IOC ioc = (IOC) sf;
                    details = String.format(" xoaOset=%d yoaOset=%d",
                            ioc.getXoaOset(), ioc.getYoaOset());
                } else if (sf instanceof GAD) {
                    GAD gad = (GAD) sf;
                    byte[] data = gad.getGOCAdat();
                    details = " gocAdatLen=" + (data != null ? data.length : 0);
                } else if (sf instanceof IRD) {
                    IRD ird = (IRD) sf;
                    byte[] imgData = ird.getIMdata();
                    details = " imageDataLen=" + (imgData != null ? imgData.length : 0);
                } else if (sf instanceof IDD) {
                    details = " IDD (Image Data Descriptor)";
                } else if (sf instanceof BeginImage) {
                    details = " BeginImage";
                } else if (sf instanceof EndImage) {
                    details = " EndImage";
                } else if (sf instanceof MFC) {
                    details = " MFC";
                } else if (sf instanceof PGD) {
                    details = " PGD";
                } else if (sf instanceof BPS) {
                    details = " psegName=" + ((BPS) sf).getPsegName();
                } else if (sf instanceof BPF) {
                    details = " pfName=" + ((BPF) sf).getPFName();
                }

                String marker = inImage ? " [IM]" : (inGraphics ? " [GR]" : "");
                String context = inDoc ? (inPage ? "  P" : " D") : "  ";
                System.out.printf("  %s#%03d: %-18s%s %s%n", context, count++, name, marker, details);

                if (count > 300) {
                    System.out.println("  ... (truncated at 300)");
                    break;
                }
            }
            System.out.println("Total SFs: " + count + ", Pages: " + pageCount);
        }
    }
}

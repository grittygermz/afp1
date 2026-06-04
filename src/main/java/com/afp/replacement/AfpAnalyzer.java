package com.afp.replacement;

import org.afplib.afplib.*;
import org.afplib.base.SF;
import org.afplib.base.Triplet;
import org.afplib.io.AfpInputStream;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AfpAnalyzer {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: AfpAnalyzer <file.afp>");
            System.exit(1);
        }
        String filePath = args[0];
        System.out.println("=== Analyzing: " + filePath + " ===\n");

        List<SF> sfs = readAll(filePath);
        dumpStructure(sfs);
    }

    public static List<SF> readAll(String filePath) throws IOException {
        List<SF> sfs = new ArrayList<>();
        try (AfpInputStream ain = new AfpInputStream(new FileInputStream(filePath))) {
            SF sf;
            while ((sf = ain.readStructuredField()) != null) {
                sfs.add(sf);
            }
        }
        return sfs;
    }

    private static boolean isBeginSF(int id) {
        return ((id >> 8) & 0xFF) == 0xA8;
    }

    private static boolean isEndSF(int id) {
        return ((id >> 8) & 0xFF) == 0xA9;
    }

    private static void dumpStructure(List<SF> sfs) {
        int depth = 0;
        for (int i = 0; i < sfs.size(); i++) {
            SF sf = sfs.get(i);
            String name = getSFName(sf);
            String details = getDetails(sf);

            if (isEndSF(sf.getId())) depth--;

            String indent = "  ".repeat(Math.max(0, depth));
            String marker = isBeginSF(sf.getId()) ? ">" : isEndSF(sf.getId()) ? "<" : " ";
            System.out.printf("%4d: %s%s %s%s%n", i, indent, marker, name, details);

            if (isBeginSF(sf.getId())) depth++;
        }
    }

    private static String getSFName(SF sf) {
        SFName sfName = SFName.get(sf.getId());
        if (sfName != null) return sfName.getName();
        return String.format("0x%06X", sf.getId());
    }

    @SuppressWarnings("unchecked")
    private static List<Triplet> getTriplets(SF sf) {
        if (sf instanceof BPS) return ((BPS) sf).getTriplets();
        if (sf instanceof BDT) return ((BDT) sf).getTriplets();
        if (sf instanceof BIM) return ((BIM) sf).getTriplets();
        if (sf instanceof BGR) return ((BGR) sf).getTriplets();
        if (sf instanceof BOC) return ((BOC) sf).getTriplets();
        if (sf instanceof BPG) return ((BPG) sf).getTriplets();
        if (sf instanceof PGD) return ((PGD) sf).getTriplets();
        if (sf instanceof BDD) return ((BDD) sf).getTriplets();
        if (sf instanceof EIM) return ((EIM) sf).getTriplets();
        if (sf instanceof EPG) return ((EPG) sf).getTriplets();
        if (sf instanceof EDT) return ((EDT) sf).getTriplets();
        return List.of();
    }

    private static String getDetails(SF sf) {
        StringBuilder sb = new StringBuilder();
        if (sf instanceof BDT) sb.append(" docName=").append(((BDT) sf).getDocName());
        if (sf instanceof BPS) sb.append(" psegName=").append(((BPS) sf).getPsegName());
        if (sf instanceof BIM) sb.append(" idoName=").append(((BIM) sf).getIdoName());
        if (sf instanceof BGR) sb.append(" gdoName=").append(((BGR) sf).getGdoName());
        if (sf instanceof BOC) sb.append(" objCName=").append(((BOC) sf).getObjCName());
        if (sf instanceof ICP) {
            ICP icp = (ICP) sf;
            sb.append(String.format(" x=%d y=%d w=%d h=%d",
                icp.getXCOset(), icp.getYCOset(), icp.getXCSize(), icp.getYCSize()));
        }
        if (sf instanceof IOC) {
            IOC ioc = (IOC) sf;
            sb.append(String.format(" xOset=%d yOset=%d xMap=%d yMap=%d",
                ioc.getXoaOset(), ioc.getYoaOset(), ioc.getXMap(), ioc.getYMap()));
        }
        if (sf instanceof IRD) {
            IRD ird = (IRD) sf;
            int len = ird.getIMdata() != null ? ird.getIMdata().length : 0;
            sb.append(" dataLen=").append(len);
        }
        if (sf instanceof PGD) {
            PGD pgd = (PGD) sf;
            sb.append(String.format(" base=%d/%d units=%d/%d size=%d/%d",
                pgd.getXpgBase(), pgd.getYpgBase(),
                pgd.getXpgUnits(), pgd.getYpgUnits(),
                pgd.getXpgSize(), pgd.getYpgSize()));
        }

        getTriplets(sf).forEach(t -> {
            if (t instanceof GBOX gbox) {
                sb.append(String.format(" GBOX[x0=%d y0=%d x1=%d y1=%d]",
                    gbox.getXPOS0(), gbox.getYPOS0(), gbox.getXPOS1(), gbox.getYPOS1()));
            }
            if (t instanceof GSECOL c) {
                sb.append(String.format(" GSECOL[color=0x%06X]", c.getCOLOR()));
            }
            if (t instanceof org.afplib.afplib.Comment c) {
                sb.append(" Comment=").append(c.getComment());
            }
        });
        return sb.toString();
    }
}

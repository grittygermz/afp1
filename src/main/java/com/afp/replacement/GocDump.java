package com.afp.replacement;

import org.afplib.base.SF;
import org.afplib.io.AfpInputStream;
import org.afplib.afplib.*;

import java.io.FileInputStream;
import java.io.IOException;

public class GocDump {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: GocDump <file.afp>");
            return;
        }
        try (AfpInputStream in = new AfpInputStream(new FileInputStream(args[0]))) {
            SF sf;
            while ((sf = in.readStructuredField()) != null) {
                if (sf instanceof GAD) {
                    byte[] data = ((GAD) sf).getGOCAdat();
                    System.out.println("GAD found! Length: " + (data != null ? data.length : 0));
                    if (data != null) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < Math.min(64, data.length); i++) {
                            sb.append(String.format("%02X ", data[i]));
                        }
                        System.out.println("Hex: " + sb);
                    }
                }
            }
        }
    }
}

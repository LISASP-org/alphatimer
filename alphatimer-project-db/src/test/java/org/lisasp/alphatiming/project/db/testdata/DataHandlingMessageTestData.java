package org.lisasp.alphatiming.project.db.testdata;

import org.lisasp.alphatimer.api.ares.serial.events.messages.values.UsedLanes;
import org.lisasp.alphatimer.api.ares.serial.Characters;

public class DataHandlingMessageTestData {
    static final byte A = 0x32;
    static final byte B = 0x53;
    static final byte C = 0x20;
    static final byte D1 = 0x3F;
    static final byte D2 = 0x3C;
    static final byte E1 = 0x20;
    static final byte E2 = 0x32;
    static final byte F1 = 0x30;
    static final byte F2 = 0x30;
    static final byte F3 = 0x31;
    static final byte G1 = 0x30;
    static final byte G2 = 0x31;
    static final byte H1 = 0x20;
    static final byte H2 = 0x30;
    static final byte I = 0x31;
    static final byte J1 = 0x20;
    static final byte J2 = 0x30;
    static final byte K1 = 0x33;
    static final byte K2 = 0x31;
    static final byte K3 = 0x3A;
    static final byte K4 = 0x32;
    static final byte K5 = 0x30;
    static final byte K6 = 0x3A;
    static final byte K7 = 0x35;
    static final byte K8 = 0x33;
    static final byte K9 = 0x2E;
    static final byte K10 = 0x39;
    static final byte K11 = 0x33;
    static final byte K12 = 0x20;

    // Example
    // Message 1: 01 02 08 32 53 20 3F 3C 20 32 30 30 31 30 31 20 20 20 30 04
    // Message 2: 01 02 08 0A 31 20 30 02 33 31 3A 32 30 3A 35 33 2E 39 33 20 04

    public static final byte[] message1 = new byte[]{Characters.SOH_StartOfHeader, Characters.STX_StartOfText, Characters.BS_CursorHome, A, B, C, D1, D2, E1, E2, F1, F2, F3, G1, G2, Characters.SPACE, Characters.SPACE, H1, H2, Characters.EOT_EndOfText};
    public static final byte[] message2 = new byte[]{Characters.SOH_StartOfHeader, Characters.STX_StartOfText, Characters.BS_CursorHome, Characters.LF_LineFeed, I, J1, J2, Characters.STX_StartOfText, K1, K2, K3, K4, K5, K6, K7, K8, K9, K10, K11, K12, Characters.EOT_EndOfText};
    public static final byte[] ping = new byte[]{Characters.SOH_StartOfHeader, Characters.DC2_Periphery, 0x39, Characters.DC4_Command, 0x54, 0x50, Characters.EOT_EndOfText};
    public static final byte[] bogus = new byte[]{0x44, 0x00, 0x45, 0x50, 0x17};

    public static UsedLanes createUsedLanes() {
        boolean[] lanes = new boolean[]{
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false};
        return new UsedLanes(lanes);
    }
}

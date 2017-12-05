package com.tarcuri.til;

/**
 * Created by Tommy on 12/5/2017.
 */

public class LC1Packet {
    private static short LC1_LAMBDA_LOW_MASK = 0x7f;
    private static short LC1_LAMBDA_HIGH_MASK = 0x3f00;
    private short header;
    private short word0;
    private short word1;

    public LC1Packet(short[] packet) {
        header = packet[0];
        word0 = packet[1];
        word1 = packet[2];
    }

    public short getHeader() { return header; }

    public short getWord0() { return word0; }

    public short getWord1() { return word1; }

    public float getLambda() {
        int l_high = (word1 & LC1_LAMBDA_HIGH_MASK) >> 1;
        int l_low = (word1 & LC1_LAMBDA_LOW_MASK);

        short L = (short) (l_high | l_low);
        return L;
    }
}

package com.tarcuri.til;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Tommy on 12/5/2017.
 */

public class LC1Packet {
    private static short LC1_LAMBDA_LOW_MASK = 0x7f;
    private static short LC1_LAMBDA_HIGH_MASK = 0x3f00;
    private static short LC1_AFR_MULT_HIGH_BIT_MASK = 1 << 8;
    private static short LC1_AFR_MULT_LOW_MASK = 0x7f;
    private short[] packet;
    private short header;
    private short word0;
    private short word1;

    public LC1Packet(short[] packet) {
        this.packet = packet;
        header = packet[0];
        word0 = packet[1];
        word1 = packet[2];
    }

    public LC1Packet(short[] packet, byte multiplier) {
        this.packet = packet;
        header = packet[0];
        word0 = packet[1];
        word1 = packet[2];
    }

    public byte[] getPacketBytes() {
        byte[] buf = new byte[6];
        ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).asShortBuffer().put(packet);
        return buf;
    }

    public short[] getPacketShorts() {
        return packet;
    }

    public short getHeader() { return header; }

    public short getWord0() { return word0; }

    public short getWord1() { return word1; }

    public short getLambdaWord() {
        int l_high = (word1 & LC1_LAMBDA_HIGH_MASK) >> 1;
        int l_low = (word1 & LC1_LAMBDA_LOW_MASK);

        return (short) (l_high | l_low);
    }

    public byte getMultiplier() {
        int m_high = (word0 & LC1_AFR_MULT_HIGH_BIT_MASK) >> 1;
        int m_low  = (word0 & LC1_AFR_MULT_LOW_MASK);

        return (byte) (m_high | m_low);
    }

    // AFR = ((L + 500) * (AF7..0) / 10000
    public float getAFR() {
        short L = getLambdaWord();
        byte multi = getMultiplier();

        float afr = (float) ((L + 500) * 147) / (float) 10000.0;

        if (afr > 22.39) {
            afr = (float) 22.39;
        }

        if (afr < 7.35) {
            afr = (float) 7.35;
        }

        return afr;
    }
}

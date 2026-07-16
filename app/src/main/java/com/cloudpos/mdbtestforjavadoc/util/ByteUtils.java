package com.cloudpos.mdbtestforjavadoc.util;

public class ByteUtils {

    public static byte[] int2byte(int value, boolean bigIndian) {
        byte[] byteValue = new byte[4];
        //update start by Hans_j at 2005-6-8 10:15:35
//      if (bigIndian) {
        if (!bigIndian) {
            //update end by Hans_j at 2005-6-8
            byteValue[0] = (byte) (value & 0xff);
            byteValue[1] = (byte) ((value & 0xff00) >>> 8);
            byteValue[2] = (byte) ((value & 0xff0000) >>> 16);
            byteValue[3] = (byte) ((value & 0xff000000) >>> 24);
        } else {
            byteValue[3] = (byte) (value & 0xff);
            byteValue[2] = (byte) ((value & 0xff00) >>> 8);
            byteValue[1] = (byte) ((value & 0xff0000) >>> 16);
            byteValue[0] = (byte) ((value & 0xff000000) >>> 24);
        }
        return byteValue;
    }
}

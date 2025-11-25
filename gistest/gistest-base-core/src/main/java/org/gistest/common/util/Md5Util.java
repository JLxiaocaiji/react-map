package org.gistest.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;

public class Md5Util {
    private static final Logger logger = LoggerFactory.getLogger(Md5Util.class);

    private static final char[] HEXDIGITS = { '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    private Md5Util() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param b 待转换的字节数组
     * @return 转换后的十六进制字符串
     */
    public static String byteArrayToHexString(byte[] b) {
        if (b == null) {
            throw new IllegalArgumentException("");
        }
        StringBuffer resultSb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            resultSb.append(byteToHexString(b[i]));
        }
        return resultSb.toString();
    }

    /**
     * 将单个字节转换为对应的两位十六进制字符
     *
     * @param b 待转换的字节
     * @return 转换后的两位十六进制字符
     */
    private static String byteToHexString(byte b) {
//        int n = b;
//        if (n < 0) {
//            n += 256;
//        }
//        int d1 = n /16;

        int n = b & 0XFF;
        char high = HEXDIGITS[n >>> 4];
        char low = HEXDIGITS[n & 0x0F];

        return new String(new char[]{high, low});
//        return new StringBuilder().append(high).append(low).toString();
    }

    public static String md5Encode(String origin, String charsetName) {
        if (origin == null) return null;

        String resultString = null;

        try {
            resultString = new String(origin);
            MessageDigest md = MessageDigest.getInstance("MD5");
            if ( charsetName == null || charsetName.trim().isEmpty()) {
                resultString = byteArrayToHexString(md.digest(resultString.getBytes()));
            } else {
                resultString = byteArrayToHexString(md.digest(resultString.getBytes(charsetName)));
            }
        } catch (Exception e) {

        }
        return resultString;
    }
}

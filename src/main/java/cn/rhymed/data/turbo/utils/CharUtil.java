package cn.rhymed.data.turbo.utils;

/**
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:50
 **/
public class CharUtil {
    /**
     * 判断字符是否为空白字符
     *
     * @param c 字符
     * @return 是否为空白字符
     */
    public static boolean isBlankChar(char c) {
        return isBlankChar((int) c);
    }


    /**
     * 判断字符是否为空白字符
     *
     * @param c 字符码
     * @return 是否为空白字符
     */
    public static boolean isBlankChar(int c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 65279 || c == 8234 || c == 0 || c == 12644 || c == 10240 || c == 6158;
    }
}

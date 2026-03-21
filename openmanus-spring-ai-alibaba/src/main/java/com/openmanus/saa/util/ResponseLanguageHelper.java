package com.openmanus.saa.util;

public final class ResponseLanguageHelper {

    private ResponseLanguageHelper() {
    }

    public enum Language {
        ZH_CN,
        EN
    }

    public static Language detect(String text) {
        if (text == null || text.isBlank()) {
            return Language.EN;
        }

        int chineseCount = 0;
        int englishLetterCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isChinese(ch)) {
                chineseCount++;
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                englishLetterCount++;
            }
        }

        return chineseCount >= englishLetterCount ? Language.ZH_CN : Language.EN;
    }

    public static String responseDirective(String text) {
        return switch (detect(text)) {
            case ZH_CN -> "Reply in Simplified Chinese. Keep tool calls and JSON fields unchanged unless the tool requires a specific format.";
            case EN -> "Reply in English. Keep tool calls and JSON fields unchanged unless the tool requires a specific format.";
        };
    }

    public static String choose(String text, String zhCn, String en) {
        return detect(text) == Language.ZH_CN ? zhCn : en;
    }

    private static boolean isChinese(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }
}

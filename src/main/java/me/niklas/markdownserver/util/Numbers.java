package me.niklas.markdownserver.util;

/**
 * Created by Niklas on 27.10.2019 in markdownserver
 */
public class Numbers {

    public static long parseLong(String input, long defaultValue) {
        try {
            return Long.parseLong(input);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}

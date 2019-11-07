package me.niklas.markdownserver.web;

import java.nio.charset.StandardCharsets;

/**
 * Created by Niklas on 15.10.2019 in markdownserver
 */
class Base64 {

    /**
     * Encodes an input using Base64.
     *
     * @param input The input.
     * @return The encoded input.
     */
    public static byte[] encode(byte[] input) {
        return java.util.Base64.getEncoder().encode(input);
    }

    /**
     * Encodes an input using Base64.
     *
     * @param input The input.
     * @return The encoded input.
     */
    public static String encode(String input) {
        return new String(java.util.Base64.getEncoder().encode(input.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Decodes an input using Base64.
     *
     * @param input The input.
     * @return The decoded input.
     */
    public static byte[] decode(byte[] input) {
        return java.util.Base64.getDecoder().decode(input);
    }

    /**
     * Decodes an input using Base64.
     *
     * @param input The input.
     * @return The decoded input.
     */
    public static String decode(String input) {
        return new String(java.util.Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
    }

    /**
     * Decodes an input using Base64 if it was encoded.
     *
     * @param input The input.
     * @return The decoded input if it was encoded. Otherwise, the original input is returned.
     */
    public static byte[] decodeOptional(byte[] input) {
        try {
            return java.util.Base64.getDecoder().decode(input);
        } catch (IllegalArgumentException exception) {
            return input;
        }
    }

    /**
     * Decodes an input using Base64 if it was encoded.
     *
     * @param input The input.
     * @return The decoded input if it was encoded. Otherwise, the original input is returned.
     */
    public static String decodeOptional(String input) {
        try {
            return new String(java.util.Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return input;
        }
    }
}

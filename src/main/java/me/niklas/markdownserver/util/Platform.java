package me.niklas.markdownserver.util;

enum Type {
    WINDOWS, MAC, LINUX, OTHER
}

/**
 * Created by Niklas on 14.10.2019 in markdownserver
 */
public class Platform {
    /**
     * The operating system.
     */
    private static final Type TYPE;

    static {
        String name = System.getProperty("os.name").toUpperCase();

        if (name.startsWith("WIN")) {
            TYPE = Type.WINDOWS;
        } else if (name.startsWith("MAC")) {
            TYPE = Type.MAC;
        } else if (name.startsWith("LIN")) {
            TYPE = Type.LINUX;
        } else {
            TYPE = Type.OTHER;
        }
    }

    public static boolean IS_WINDOWS() {
        return TYPE == Type.WINDOWS;
    }

    public static boolean IS_MAC() {
        return TYPE == Type.MAC;
    }

    public static boolean IS_LINUX() {
        return TYPE == Type.LINUX;
    }

    public static boolean IS_UNKNOWN() {
        return TYPE == Type.OTHER;
    }
}

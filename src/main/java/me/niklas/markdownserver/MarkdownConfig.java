package me.niklas.markdownserver;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.util.Collections;

/**
 * Created by Niklas on 14.10.2019 in markdownserver
 */
public class MarkdownConfig {

    static final MutableDataSet OPTIONS = new MutableDataSet();
    public static String DROPDOWN_NAME = "Folders";
    public static Object SERVER_NAME = "Markdown Server";
    public static String HOST = "127.0.0.1";
    public static int PORT = 1278;
    public static String ROOT = "/index.html";
    public static boolean LIVE_RELOAD = true;
    public static long COOKIE_AGE = 2592000;

    static {
        OPTIONS.set(Parser.EXTENSIONS, Collections.singletonList(TablesExtension.create()));
    }
}

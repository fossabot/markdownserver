package me.niklas.markdownserver.util;

import me.niklas.markdownserver.MarkdownFile;

import java.util.Comparator;

/**
 * Created by Niklas on 26.10.2019 in markdownserver
 */
public class MarkdownComparator implements Comparator<MarkdownFile> {
    @Override
    public int compare(MarkdownFile o1, MarkdownFile o2) {
        String u1 = o1.getUrl();
        String u2 = o2.getUrl();

        if (slashCount(u1) != slashCount(u2)) {
            return Integer.compare(slashCount(u1), slashCount(u2));
        }

        return u1.compareTo(u2);
    }

    private int slashCount(String input) {
        return input.length() - input.replace("/", "").length();
    }
}

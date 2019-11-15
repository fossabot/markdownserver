package me.niklas.markdownserver.fs;

import me.niklas.markdownserver.MarkdownFile;
import me.niklas.markdownserver.util.MarkdownComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Niklas on 24.10.2019 in markdownserver
 */
public class MarkdownFilesManager {

    private final List<MarkdownFile> files = new ArrayList<>();
    private final Map<String, String> indexFiles = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final File rd;
    private String dropdown = "";

    public MarkdownFilesManager(File rd) {
        this.rd = rd;
    }

    public void rescan() {
        scanMarkdownFiles(rd);
        generateDropdowns();
    }

    private void scanMarkdownFiles(File dir) {
        if (!dir.isDirectory()) return;
        if (dir.equals(rd)) {
            logger.info("RELOADING MARKDOWN FILES");
            files.clear();
            indexFiles.clear();
        }


        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.isDirectory()) scanMarkdownFiles(file);
            else if (file.getName().endsWith(".md")) {
                files.add(new MarkdownFile(file, rd));
            } else if (file.getName().equals(".mdIndex")) {
                try {
                    String redirect = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8).get(0);

                    String baseUrl = file.getParentFile().getAbsolutePath().substring(rd.getAbsolutePath().length())
                            .replace(File.separator, "/").replace(" ", "-").toLowerCase();
                    if (baseUrl.length() == 0) baseUrl = "/";
                    indexFiles.put(baseUrl, redirect);
                } catch (Exception e) {
                    logger.error("Can not read index file at " + file.getAbsolutePath(), e);
                }
            }
        }

        if (dir.equals(rd)) { //Scanning is done
            logger.info("Files found: " + files.size());
            logger.info("Indices:" + indexFiles.size());
        }
    }

    private void generateDropdowns() {
        if (rd.listFiles() == null) return;
        List<String> folders = new ArrayList<>();

        for (File dir : rd.listFiles()) {
            if (!dir.isDirectory() || dir.getName().equals("resources") || dir.getName().startsWith(".")) continue;
            folders.add(dir.getName());
        }

        StringBuilder builder = new StringBuilder();
        folders.forEach(name -> {
            String link = "/" + name.replace(" ", "-").toLowerCase();
            builder.append("<a class=\"dropdown-item\" href=\"").append(link).append("\">").append(name).append("</a>\n");
        });
        dropdown = builder.toString().trim();
    }

    public Optional<MarkdownFile> getFile(String path) {
        return files.stream().filter(f -> f.getUrl().equals(path)).findFirst();
    }

    public String getDropdown() {
        return dropdown;
    }

    public List<MarkdownFile> getFolderOverview(String path) { //Filter to files matching path, sort them, then return them as a List.
        return files.stream().filter(file -> file.getUrl().startsWith(path)).sorted(new MarkdownComparator()).collect(Collectors.toList());
    }

    public String generateFolderHtml(List<MarkdownFile> files) {
        StringBuilder builder = new StringBuilder("<h1>Ordnerübersicht</h1><ul>");

        files.forEach(file -> builder.append("<li><a href=\"").append(file.getUrl()).append("\">").append(file.getUrl()).append("</a></li>"));

        return builder.append("</ul>").toString().trim();
    }

    public boolean hasIndexFile(String path) {
        logger.debug("Checking index for path " + path);
        return indexFiles.containsKey(path);
    }

    public String getRedirectPath(String path) {
        return indexFiles.getOrDefault(path, "/");
    }
}

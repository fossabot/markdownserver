package me.niklas.markdownserver;

import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.html.AttributeProvider;
import com.vladsch.flexmark.html.AttributeProviderFactory;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.IndependentAttributeProviderFactory;
import com.vladsch.flexmark.html.renderer.AttributablePart;
import com.vladsch.flexmark.html.renderer.LinkResolverContext;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.html.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.Objects;

/**
 * Created by Niklas on 14.10.2019 in markdownserver
 */
public class MarkdownFile {

    private static final Parser PARSER = Parser.builder(MarkdownConfig.OPTIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder(MarkdownConfig.OPTIONS).attributeProviderFactory(ChangeProvider.Factory()).build();
    private final Logger logger;
    private final File file;
    private final String url;
    private String content;
    private String html;

    public MarkdownFile(File file, File root) {
        Objects.requireNonNull(file);
        Objects.requireNonNull(root);
        logger = LoggerFactory.getLogger("Logger of " + file.getName());
        this.file = file;

        if (!file.getAbsolutePath().startsWith(root.getAbsolutePath())) {
            url = "/error";
            logger.error("Root file is not a parent directory!");
        } else
            url = file.getAbsolutePath().substring(root.getAbsolutePath().length(), file.getAbsolutePath().length() - 3)
                    .replace(File.separator, "/").replace(" ", "-").toLowerCase();
    }

    public File getFile() {
        return file;
    }

    public String getUrl() {
        return url;
    }

    private void readContent() {
        if (!file.exists()) {
            logger.error("File does not exist");
            return;
        }
        try {
            StringBuilder builder = new StringBuilder();
            Files.readAllLines(file.toPath()).forEach(line -> builder.append(line).append("\n"));
            content = builder.toString().substring(0, builder.toString().length() - 1); //Remove last \n
        } catch (Exception e) {
            logger.error("Could not read content", e);
        }
    }

    public String getTitle() {
        Document doc = getDocument();

        return doc.getFirstChild().getChars().toString().replace("#", "").trim();
    }

    private Document getDocument() {
        if (content == null || content.length() == 0) readContent();

        return MarkdownFile.PARSER.parse(content);
    }

    private void generateHtml() {
        html = RENDERER.render(getDocument());
    }

    public String getContent() {
        if (content == null || content.length() == 0) readContent();
        return content;
    }

    public String getHtml() {
        if (html == null) {
            generateHtml();
        }
        return html;
    }

    static class ChangeProvider implements AttributeProvider {
        static AttributeProviderFactory Factory() {
            return new IndependentAttributeProviderFactory() {
                @Override
                public AttributeProvider apply(LinkResolverContext context) {
                    return new ChangeProvider();
                }
            };
        }

        @Override
        public void setAttributes(Node node, AttributablePart part, Attributes attributes) {
            if (node instanceof Link && part == AttributablePart.LINK) {
                if (!attributes.getValue("href").startsWith("/") || attributes.getValue("href").startsWith("/resources")) {
                    attributes.replaceValue("target", "_blank");
                }
            } else if (node instanceof Image) {
                attributes.addValue("class", "img-fluid");
            }
        }
    }
}

package me.niklas.markdownserver.configuration;

import me.niklas.markdownserver.MarkdownConfig;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Created by Niklas on 15.10.2019 in markdownserver
 */
public class ConfigurationProvider {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final File propertiesFile;
    private FileBasedConfigurationBuilder<FileBasedConfiguration> builder;
    private Configuration config;

    public ConfigurationProvider(File directory) {
        this.propertiesFile = new File(directory.getAbsolutePath() + File.separatorChar + "configuration.properties");

        if (!propertiesFile.exists()) {
            try {

                propertiesFile.createNewFile();
            } catch (IOException e) {
                logger.error("Can not create properties file", e);
            }
        }
        buildConfigFile();
        buildDefault();
        readProperties();
    }

    private void readProperties() {
        MarkdownConfig.SERVER_NAME = config.getString("servername");
        MarkdownConfig.DROPDOWN_NAME = config.getString("dropdownName");
        MarkdownConfig.HOST = config.getString("host");
        MarkdownConfig.PORT = config.getInt("port");
        MarkdownConfig.ROOT = config.getString("root");
        MarkdownConfig.LIVE_RELOAD = config.getBoolean("liveReload");
        MarkdownConfig.COOKIE_AGE = config.getLong("cookieAge");
    }

    private void buildConfigFile() {
        Parameters params = new Parameters();
        params.properties().setFile(propertiesFile);
        params.properties().setEncoding("UTF-8");

        builder =
                new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
                        .configure(params.properties()
                                .setFile(propertiesFile)
                                .setEncoding("UTF-8")
                                .setListDelimiterHandler(new DefaultListDelimiterHandler(',')));

        try {
            config = builder.getConfiguration();
        } catch (ConfigurationException e) {
            logger.error("Can not load properties", e);
        }

    }

    private void buildDefault() {
        setDefault("servername", "Markdown Server");
        setDefault("dropdownName", "Folders");
        setDefault("host", "127.0.0.1");
        setDefault("port", 1278);
        setDefault("root", "/");
        setDefault("liveReload", true);
        setDefault("cookieAge", 2592000);

        saveConfig();
    }

    private void saveConfig() {
        try {
            builder.save();
        } catch (ConfigurationException e) {
            logger.error("Can not save config", e);
        }
    }

    private void setDefault(String key, Object value) {
        if (!config.containsKey(key)) config.addProperty(key, value);
    }
}

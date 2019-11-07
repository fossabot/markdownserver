package me.niklas.markdownserver.configuration;

import me.niklas.markdownserver.util.User;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by Niklas on 27.10.2019 in markdownserver
 */
public class SessionsManager {

    private final Map<String, User> cache = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final File propertiesFile;
    private FileBasedConfigurationBuilder<FileBasedConfiguration> builder;
    private Configuration config;

    private long lastUpdate;

    public SessionsManager(File dir) {
        this.propertiesFile = new File(dir.getAbsolutePath() + File.separatorChar + "sessions.properties");

        if (!propertiesFile.exists()) {
            try {

                propertiesFile.createNewFile();
            } catch (IOException e) {
                logger.error("Can not create properties file", e);
            }
        }
        reload();
    }

    public void invalidateAll() {
        cache.clear();
        config.clear();
        saveConfig();
    }

    public void invalidateSessionsForUser(String username) {
        if (username == null || username.length() == 0) return;

        List<String> keys = new ArrayList<>();
        cache.forEach((id, user) -> {
            if (user.getName().equals(username)) {
                keys.add(id);
            }
        });

        keys.forEach(key -> {
            cache.remove(key);
            config.clearProperty(key);
        });

        saveConfig();
    }

    public void invalidateId(String loginId) {
        invalidateId(loginId, true);
    }

    private void invalidateId(String loginId, boolean save) {
        if (config.containsKey(loginId)) {
            cache.remove(loginId);
            config.clearProperty(loginId);
            if (save) saveConfig();
        }
    }

    public Map<String, User> getSessions() {
        return cache;
    }

    public void reload() {
        if (System.currentTimeMillis() - lastUpdate < 5000) {
            logger.info("Blocking update");
            return;
        }
        update();
        buildConfigFile();

        cache.clear();
        Iterator<String> it = config.getKeys();
        while (it.hasNext()) {
            String key = it.next();
            try {
                cache.put(key, new User(config.getStringArray(key)));
            } catch (Exception e) {
                logger.error("Can not cast key " + key + " to User", e);
            }
        }

        List<String> invalidKeys = new ArrayList<>();
        cache.forEach((id, user) -> {
            if (user.isInvalid()) invalidKeys.add(id);
        });

        if (invalidKeys.size() > 0) {
            logger.debug("Invalid keys: ");
            invalidKeys.forEach(key -> logger.debug(key + " = " + cache.get(key).toString()));
        }

        invalidKeys.forEach(key -> invalidateId(key, false));
        if (invalidKeys.size() > 0) saveConfig();
        logger.info("Sessions: " + cache.size());
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
                                .setListDelimiterHandler(new DefaultListDelimiterHandler('`')));

        try {
            config = builder.getConfiguration();
        } catch (ConfigurationException e) {
            logger.error("Can not load properties", e);
        }

    }

    private void saveConfig() {
        try {
            builder.save();
        } catch (ConfigurationException e) {
            logger.error("Can not save config", e);
        }
    }

    /**
     * @param ip       The IP address
     * @param username The username.
     * @return The login id or null if the parameters are invalid
     */
    public String createSession(String ip, String username) {
        String lid = RandomStringUtils.random(32, true, true);

        User user = new User(ip, username, System.currentTimeMillis());
        cache.put(lid, user);
        config.setProperty(lid, user.toString());
        logger.info("Saved cookie: " + lid);
        update();
        saveConfig();
        return lid;
    }

    private void update() {
        lastUpdate = System.currentTimeMillis();
    }

    public boolean hasSession(String loginId) {
        if (loginId == null) {
            logger.warn("Invalid request format in hasSession");
            return false;
        }
        if (!cache.containsKey(loginId)) {
            logger.info("loginId is not stored");
            return false;
        }
        return true;
    }

    public String getNameById(String loginId) {
        if (loginId == null || !cache.containsKey(loginId)) return null;
        return cache.get(loginId).getName();
    }

    public User getSession(String cookie) {
        if (cookie == null) return null;
        return cache.getOrDefault(cookie, null);
    }
}

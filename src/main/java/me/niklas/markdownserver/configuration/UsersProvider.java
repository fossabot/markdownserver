package me.niklas.markdownserver.configuration;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Helper;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by Niklas on 19.10.2019 in markdownserver
 */
public class UsersProvider {
    private static final List<String> CLAIMED = Arrays.asList("iterations", "admin", "master", "adminOnly");
    private final int memory = 65536;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Argon2 argon = Argon2Factory.create();
    private final File propertiesFile;
    private int iterations;
    private FileBasedConfigurationBuilder<FileBasedConfiguration> builder;
    private Configuration config;
    private long lastUpdate;

    public UsersProvider(File directory) {
        this.propertiesFile = new File(directory.getAbsolutePath() + File.separatorChar + "users.properties");

        if (!propertiesFile.exists()) {
            try {

                propertiesFile.createNewFile();
            } catch (IOException e) {
                logger.error("Can not create properties file", e);
            }
        }
        reload();
    }

    public void reload() {
        if (System.currentTimeMillis() - lastUpdate < 5000) {
            logger.info("Blocking update");
            return;
        }
        update();
        buildConfigFile();
        buildDefault();
    }

    public boolean isAdmin(Request request) {
        return isAdminByName(request.session(true).attribute("username"));
    }

    public boolean isMaster(Request request) {
        return isMasterByName(request.session(true).attribute("username"));
    }

    public boolean isMasterByName(String username) {
        if (username == null || username.length() == 0 || CLAIMED.contains(username)) return false;
        return config.getString("master", "").equals(username);
    }

    public boolean isAdminByName(String username) {
        if (username == null || username.length() == 0 || CLAIMED.contains(username)) return false;
        if (config.getString("master", "").equals(username)) return true;

        String[] admin = config.getStringArray("admin");

        for (String s : admin) {
            if (s.equals(username)) return true;
        }
        return false;
    }

    public boolean addAdmin(String username, String author) {
        if (!config.getString("master").equals(author)) return false;
        if (CLAIMED.contains(username)) return false;

        String[] array = config.getStringArray("admin");

        for (String s : array) { //Ensure that username is not Admin yet
            if (s.equals(username)) return false;
        }

        String[] larger = new String[array.length + 1]; //Copy into larger array

        System.arraycopy(array, 0, larger, 0, array.length);
        larger[array.length] = username; //Add user at last index
        config.setProperty("admin", larger);
        saveConfig();
        return true;
    }

    public void removeAdmin(String username, String author) {
        if (!config.getString("master").equals(author)) return;
        if (CLAIMED.contains(username)) return;

        int index = -1;

        String[] original = config.getStringArray("admin");
        for (int i = 0; i < original.length; i++) {
            if (original[i].equals(username)) index = i;
        }
        if (index == -1) return;

        int largerIndex = 0;

        String[] smaller = new String[original.length - 1];

        for (int i = 0; i < smaller.length; i++) {
            if (largerIndex == index) largerIndex++;
            smaller[i] = original[largerIndex];
            largerIndex++;
        }
        config.setProperty("admin", smaller);
        saveConfig();
    }

    public boolean verifyLogin(String username, String password) {
        if (!config.containsKey(username) || CLAIMED.contains("username")) return false;
        boolean valid = argon.verify(config.getString(username), password.toCharArray());
        argon.wipeArray(password.toCharArray());
        return valid;
    }

    public boolean removeUser(String username) {
        if (!config.containsKey(username) || CLAIMED.contains("username")) return false;
        config.clearProperty(username);
        removeAdmin(username, config.getString("master"));
        saveConfig();
        return true;
    }

    public boolean addUser(String username, String password) {
        if (config.containsKey(username) || CLAIMED.contains(username)) return false;
        config.setProperty(username, argon.hash(iterations, memory, 1, password.toCharArray()));
        saveConfig();
        return true;
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

    private void buildDefault() {
        boolean altered = false;
        Iterator<String> keys = config.getKeys();

        while (keys.hasNext()) logger.info("Key: " + keys.next());
        logger.info("Config size: " + config.size());
        if (!config.containsKey("iterations")) {
            logger.info("Finding optimal iterations");
            iterations = Argon2Helper.findIterations(argon, 1000, memory, 1);
            logger.info("Using " + iterations + " iterations.");
            config.setProperty("iterations", iterations);
            altered = true;
        } else {
            iterations = config.getInt("iterations");
        }

        if (setDefault("admin", new String[]{"adminUser"})) altered = true;
        if (setDefault("master", "masterUser")) altered = true;
        if (setDefault("adminOnly", "true")) altered = true;

        if (config.size() < CLAIMED.size() + 1) { //Three: Admin, Master,
            logger.info("Building standard user...");
            String username = "masterUser";
            String password = RandomStringUtils.random(8, true, true);
            logger.info(String.format("Username: %s ; Password: %s", username, password));
            logger.info("Change this password as soon as possible!");
            String hash = argon.hash(iterations, memory, 1, password.toCharArray());
            config.setProperty(username, hash);
            logger.info("Is able to do anything? " + (argon.verify(hash, "StandardPassword!713".toCharArray())));
            altered = true;
        }
        if (altered) saveConfig();
    }

    private boolean setDefault(String key, Object value) {
        if (!config.containsKey(key)) {
            config.addProperty(key, value);
            return true;
        }
        return false;
    }


    private void saveConfig() {
        try {
            builder.save();
        } catch (ConfigurationException e) {
            logger.error("Can not save config", e);
        }
    }

    public boolean changePassword(Request request, Response response) {
        String username = request.session(true).attribute("username");
        if (username == null) {
            logger.warn("No username is session");
            return false;
        }
        String currentPass = request.queryParams("current");
        String newPass = request.queryParams("new");

        if (currentPass == null || newPass == null) {
            logger.warn("Invalid params in changePassword");
            return false;
        }
        if (!verifyLogin(username, currentPass)) {
            logger.warn("Invalid old password in changePassword");
            return false;
        }

        config.setProperty(username, argon.hash(iterations, memory, 1, newPass.toCharArray()));
        saveConfig();
        return true;
    }

    public boolean changePassword(Request request, String username, String password) {
        if (!isMaster(request)) return false;
        config.setProperty(username, argon.hash(iterations, memory, 1, password.toCharArray()));
        saveConfig();
        return true;
    }

    public List<String> getUsernames() {
        Iterator<String> keys = config.getKeys();
        List<String> result = new ArrayList<>();

        while (keys.hasNext()) {
            String i = keys.next();
            if (!CLAIMED.contains(i)) result.add(i);
        }
        Collections.sort(result);

        return result;
    }

    public boolean toggleAdminOnly() {
        config.setProperty("adminOnly", config.getBoolean("adminOnly") ? "false" : "true");
        update();
        saveConfig();
        return config.getBoolean("adminOnly");
    }

    private void update() {
        lastUpdate = System.currentTimeMillis();
    }

    public boolean blockByAdminOnlyMode(Request request) {
        if (!isAdminOnly()) return false;
        return !isAdmin(request);
    }

    public boolean isAdminOnly() {
        return config.getBoolean("adminOnly");
    }
}

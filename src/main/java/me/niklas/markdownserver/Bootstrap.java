package me.niklas.markdownserver;

import me.niklas.markdownserver.configuration.ConfigurationProvider;
import me.niklas.markdownserver.web.Server;

import java.io.File;

/**
 * Created by Niklas on 14.10.2019 in markdownserver
 */
public class Bootstrap {

    public static void main(String[] args) {
        File rd = new File(args.length > 0 ? String.join(" ", args).trim() : System.getProperty("user.dir"));
        new ConfigurationProvider(rd);
        new Thread(new Server(rd)).start();
    }
}

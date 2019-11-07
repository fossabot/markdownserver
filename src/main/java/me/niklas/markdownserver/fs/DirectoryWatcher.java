package me.niklas.markdownserver.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Created by Niklas on 14.10.2019 in markdownserver
 */
public class DirectoryWatcher {

    public DirectoryWatcher(Runnable runOnNormalEvent, Runnable runOnConfigEvent, File dir) {

        Logger logger = LoggerFactory.getLogger("DirectoryWatcher");
        logger.info("DIRECTORY WATCHER: RUNNING ON " + dir.getAbsolutePath());
        try {
            WatchService watcher = FileSystems.getDefault().newWatchService();
            Path path = dir.toPath();
            path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

            File[] subdirectories;
            if ((subdirectories = dir.listFiles()) != null) {
                for (File file : subdirectories) {
                    if (file.isDirectory()) file.toPath().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                }
            }

            long lastConfigUpdate = 0;
            long lastFileUpdate = 0;

            boolean run = true;
            while (run) {
                try {
                    WatchKey key = watcher.take();

                    if (key == null) {
                        logger.info("POLLED NOTHING; CONTINUE!");
                        continue;
                    }

                    Thread.sleep(3000);

                    boolean reloadNormal = false;
                    boolean reloadConfig = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == OVERFLOW) continue;

                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path filename = ev.context();
                        if (filename.toString().contains(".properties")) reloadConfig = true;
                        else reloadNormal = true;
                    }
                    if (reloadNormal && (System.currentTimeMillis() - lastFileUpdate) > 1000) {
                        lastFileUpdate = System.currentTimeMillis();
                        logger.info("FIRING NORMAL UPDATE EVENT");
                        runOnNormalEvent.run();
                    }
                    if (reloadConfig && (System.currentTimeMillis() - lastConfigUpdate) > 1000) {
                        lastConfigUpdate = System.currentTimeMillis();
                        logger.info("FIRING CONFIG UPDATE EVENT");
                        runOnConfigEvent.run();
                    }
                    run = key.reset();
                } catch (Exception e) {
                    logger.error("Error in watcher iteration", e);
                }
            }

            logger.warn("DIRECTORY WATCHER: STOPPED");
        } catch (Exception e) {
            logger.error("Failed at watching directory", e);
            logger.error("Directory watcher STOPPED");
        }
    }
}

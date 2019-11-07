package me.niklas.markdownserver.util;

import me.niklas.markdownserver.MarkdownConfig;

/**
 * Created by Niklas on 19.10.2019 in markdownserver
 */
public class User {

    public static final User EMPTY = new User("IP-UNKNOWN", "", 0);
    private final String ip;
    private final String name;
    private final long creationTime;

    public User(String ip, String name, long creationTime) {
        this.ip = ip;
        this.name = name;
        this.creationTime = creationTime;
    }

    public User(String... parts) {
        ip = parts.length > 0 ? parts[0] : "IP-UNKNOWN";
        name = parts.length > 1 ? parts[1] : "";
        creationTime = parts.length > 2 ? Numbers.parseLong(parts[2], 0) : 0;
    }

    public String getIp() {
        return ip;
    }

    public String getName() {
        return name;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public boolean isInvalid() {
        return getTimeLeft() <= 0;
    }

    private long getTimePassed() {
        return System.currentTimeMillis() - creationTime;
    }

    private long getTimeLeft() {
        return MarkdownConfig.COOKIE_AGE - getTimePassed();
    }

    private boolean equals(String ip, String name) {
        return this.ip.equals(ip) && this.name.equals(name);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof User)) return false;
        User u = (User) obj;
        return equals(u.ip, u.name);
    }

    @Override
    public String toString() {
        return String.format("%s`%s`%d", ip, name, creationTime);
    }
}

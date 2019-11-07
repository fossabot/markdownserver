package me.niklas.markdownserver.util;

import spark.Request;

/**
 * Created by Niklas on 27.10.2019 in markdownserver
 */
public class IpUtils {

    /**
     * Use this method to compensate the use of reverse proxies.
     *
     * @param request The request of the client, it will contain "X-Forwarded-For" if it was sent through a reverse proxy.
     * @return The Client's IP address.
     */
    public static String getIp(Request request) {
        if (request.headers().contains("X-Forwarded-For")) {
            return request.headers("X-Forwarded-For");
        }
        return request.ip();
    }
}

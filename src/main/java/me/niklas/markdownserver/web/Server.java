package me.niklas.markdownserver.web;

import me.niklas.markdownserver.MarkdownConfig;
import me.niklas.markdownserver.MarkdownFile;
import me.niklas.markdownserver.configuration.SessionsManager;
import me.niklas.markdownserver.configuration.UsersProvider;
import me.niklas.markdownserver.fs.DirectoryWatcher;
import me.niklas.markdownserver.fs.MarkdownFilesManager;
import me.niklas.markdownserver.util.IpUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Service;
import spark.template.velocity.VelocityTemplateEngine;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Niklas on 14.10.2019 in markdownserver
 */
public class Server implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(getClass()); //The logger
    private final UsersProvider users;
    private final SessionsManager sessionsManager; //Manages the user sessions
    private final MarkdownFilesManager mdManager; //Manages the markdown files

    private final File rd; //Running directory (where the files are located)
    private final ReentrantLock lock = new ReentrantLock(); //The lock used to prevent that data is requested too early
    private final AtomicBoolean started = new AtomicBoolean(false);
    private boolean running = false;

    public Server(File runningDirectory) {
        this.rd = runningDirectory;
        this.sessionsManager = new SessionsManager(rd);
        this.users = new UsersProvider(rd);

        mdManager = new MarkdownFilesManager(rd);
        mdManager.rescan();

        Thread t = new Thread(() -> new DirectoryWatcher(mdManager::rescan, this::reload, rd));
        if (MarkdownConfig.LIVE_RELOAD) t.start();
    }

    private void reload() {
        users.reload();
        sessionsManager.reload();
    }

    @Override
    public void run() {
        if (running) return;
        lock.lock();
        running = true;

        Service http = Service.ignite().staticFileLocation("static").externalStaticFileLocation(rd.getAbsolutePath() + File.separatorChar + "resources").ipAddress(MarkdownConfig.HOST).port(MarkdownConfig.PORT);

        if (!MarkdownConfig.ROOT.equals("/") && MarkdownConfig.ROOT.length() > 0) {
            http.get("/", (request, response) -> {
                response.redirect(MarkdownConfig.ROOT);
                return "Redirecting <a href=\"" + MarkdownConfig.ROOT + "\">here</a>";
            });
        }

        http.before("*", ((request, response) -> {
            if (request.pathInfo().equals("/robots.txt")) return;
            if (!verifyLogin(request) && !request.pathInfo().equals("/login")) {
                request.session(true).attribute("redirect", request.pathInfo());
                response.redirect("/login");
                http.halt(301);
                return;
            } else if (request.pathInfo().equals("/login") && verifyLogin(request)) {
                response.redirect("/");
                http.halt(301);
                return;
            }

            if (!request.pathInfo().equals("/login") && users.blockByAdminOnlyMode(request)) {
                logout(request, response);
                response.redirect("/login");
                http.halt(301);
                return;
            }
            if (request.pathInfo().startsWith("/resources")) {
                response.redirect(request.pathInfo().substring(10));
            }
        }));

        http.get("/login", (request, response) -> {

            if (users.blockByAdminOnlyMode(request)) {
                return new ModelAndView(new HashMap<String, Object>(), "locked.html");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("showInvalid", request.queryParams().contains("retry") ? "" : "display: none;");
            String redirect = request.session(true).attribute("redirect");
            redirect = redirect == null ? "Startseite" : redirect.equals("/") ? "Startseite" : redirect;
            data.put("redirect", redirect);
            return new ModelAndView(data, "login.html");
        }, new VelocityTemplateEngine());

        http.post("/login", ((request, response) -> {
            logger.info("Receiving login try");
            boolean success = tryLogin(request, response);
            if (success) {
                String redirect = request.session(true).attribute("redirect");
                response.redirect(redirect == null ? "/" : redirect);
                return "Redirecting";
            } else {
                response.redirect("/login?retry=true");
                return "Invalid username or password";
            }
        }));

        http.get("/renewpw", (request, response) -> {

            Map<String, Object> data = new HashMap<>();
            data.put("showInvalid", request.queryParams().contains("retry") ? "" : "display: none;");
            return new ModelAndView(data, "renewpw.html");
        }, new VelocityTemplateEngine());

        http.post("/renewpw", ((request, response) -> {
            boolean success = users.changePassword(request, response);
            if (success) {
                sessionsManager.invalidateSessionsForUser(request.session(true).attribute("username"));
                sessionsManager.invalidateId(request.cookie("login-id"));
                response.removeCookie("login-id");
                response.redirect("/");
                return "Redirecting";
            } else {
                response.redirect("/renewpw?retry=true");
                return "Invalid old password";
            }
        }));

        http.post("/renewpw/master", ((request, response) -> {
            if (!users.isMaster(request)) {
                response.status(401);
                return "You are not authorized to do this.";
            }

            JSONObject json = new JSONObject(request.body());
            String username;
            String password;
            if (!json.has("username") || !json.has("password")
                    || (username = json.getString("username")).length() == 0
                    || (password = json.getString("password")).length() == 0) {
                response.status(400);
                return "Please provide correct data!";
            }

            return users.changePassword(request, username, password) ? "Password was changed successfully." : "You don't have permission to do this.";
        }));

        http.get("/logout", ((request, response) -> {
            logout(request, response);
            response.redirect("/login");
            return "Redirecting";
        }));

        http.get("/admin", ((request, response) -> {
            if (!users.isAdmin(request)) {
                response.status(401);
                return "You are not authorized to do this.";
            }
            Map<String, Object> model = new HashMap<>();
            model.put("title", "Admin-Panel");
            model.put("username", request.session(true).attribute("username"));
            ModelAndView mv = new ModelAndView(model, "admin.html");
            return new VelocityTemplateEngine().render(mv);
        }));

        http.get("/sitemap", ((request, response) -> {
            //Find folder overview
            List<MarkdownFile> files = mdManager.getFolderOverview("/");

            //Create overview
            Map<String, Object> model = new HashMap<>();
            model.put("title", "Ordnerübersicht");
            model.put("data", mdManager.generateFolderHtml(files));
            model.put("username", request.session(true).attribute("username"));
            ModelAndView mv = new ModelAndView(model, "template.html");
            return new VelocityTemplateEngine().render(mv);
        }));

        http.get("/nav", ((request, response) -> {
            Map<String, Object> model = new HashMap<>();
            fillNavbar(model, request, response);
            ModelAndView mv = new ModelAndView(model, "nav.html");
            return new VelocityTemplateEngine().render(mv);
        }));

        http.get("/invalidate", (((request, response) -> {
            if (!users.isMaster(request)) {
                response.status(401);
                return "You are not authorized to do this.";
            }
            sessionsManager.invalidateAll();
            return "All user sessions have been deleted.";
        })));

        http.get("/invalidate/:id", (((request, response) -> {
            if (!users.isMaster(request)) {
                response.status(401);
                return "You are not authorized to do this.";
            }
            sessionsManager.invalidateId(request.params("id"));
            return "This sessions has been removed.";
        })));

        http.get("/sessions", ((request, response) -> {
            if (!users.isAdmin(request)) {
                response.status(401);
                return "You are not authorized to do this.";
            }
            StringBuilder builder = new StringBuilder();

            builder.append("<h5>Users</h5>\n<ul>\n");
            users.getUsernames().forEach(name -> builder.append("<li>").append(name).append("</li>\n"));
            builder.append("</ul><h5>Sessions</h5>\n<ul>\n");


            sessionsManager.getSessions().forEach((key, value) -> builder.append(String.format("<li><i>%s</i> is <b>%s from %s</b>.</li>\n", key, value.getName(), value.getIp())));
            builder.append("</ul>");
            return builder.toString().trim();
        }));

        http.post("/adminonly", ((request, response) -> {
            if (!users.isAdmin(request)) {
                response.status(401);
                return "You are not authorized to do this.";
            }

            boolean now = users.toggleAdminOnly();
            return now ? "Admin-only mode is now enabled." : "Admin-only mode is now disabled.";
        }));

        http.get("/adminonly/toggletext", ((request, response) -> users.isAdminOnly() ? "Disable" : "Enable"));

        http.post("/users/add", ((request, response) -> {
            if (!users.isAdmin(request)) {
                response.status(401);
                return "You are not authorized to do this.";
            }

            JSONObject json = new JSONObject(request.body());
            String username;
            String password;
            if (!json.has("username") || !json.has("password")
                    || (username = json.getString("username")).length() == 0
                    || (password = json.getString("password")).length() == 0) {
                response.status(400);
                return "Please provide correct data!";
            }

            return users.addUser(username, password) ? "User was created successfully." : "This user could not be created.";
        }));

        http.post("/users/remove", ((request, response) -> {
            if (!users.isAdmin(request)) {
                response.status(401);
                return "You are not authorized to do this.";
            }
            JSONObject json = new JSONObject(request.body());
            String username;
            if (!json.has("username") || (username = json.getString("username")).length() == 0) {
                response.status(400);
                return "Please provide correct data!";
            }
            return users.removeUser(username) ? "User was deleted successfully." : "This user could not be deleted.";
        }));

        http.get("/error", ((request, response) -> "An error occurred."));

        http.get("/reload", ((request, response) -> {
            mdManager.rescan();
            users.reload();
            response.redirect("/");
            return "Redirecting";
        }));

        http.notFound(((request, response) -> {
            String path = request.pathInfo();
            try {

                //Find Markdown file matching this route
                Optional<MarkdownFile> result = mdManager.getFile(path);

                if (result.isPresent()) {

                    response.status(200); //OK
                    Map<String, Object> model = new HashMap<>();
                    model.put("title", result.get().getTitle());
                    model.put("data", result.get().getHtml());
                    model.put("username", request.session(true).attribute("username"));
                    ModelAndView mv = new ModelAndView(model, "template.html");
                    return new VelocityTemplateEngine().render(mv);
                }

                //Check whether it has an index
                if (path.length() > 1 && path.charAt(path.length() - 1) == '/')
                    path = path.substring(0, path.length() - 1);
                if (mdManager.hasIndexFile(path)) {
                    response.redirect(mdManager.getRedirectPath(path));
                    return "Redirecting";
                }

                //Find folder overview
                List<MarkdownFile> files = mdManager.getFolderOverview(path);

                if (files.size() == 0) { //Error 404
                    response.status(404); //Not found
                    Map<String, Object> model = new HashMap<>();
                    model.put("title", "Nicht gefunden");
                    model.put("data", "<h1>Die angefragte Seite wurde nicht gefunden.</h1>\n" +
                            "<h3><a href=\"/\">Hier</a> findest du zurück zur Startseite.</h3>");
                    model.put("username", request.session(true).attribute("username"));
                    ModelAndView mv = new ModelAndView(model, "template.html");
                    return new VelocityTemplateEngine().render(mv);
                }

                //Create overview
                Map<String, Object> model = new HashMap<>();
                model.put("title", "Ordnerübersicht");
                model.put("data", mdManager.generateFolderHtml(files));
                model.put("username", request.session(true).attribute("username"));
                ModelAndView mv = new ModelAndView(model, "template.html");
                return new VelocityTemplateEngine().render(mv);


            } catch (Exception e) {
                logger.error("Can not serve " + path, e);
                return "Peinlich, peinlich... da ist etwas schiefgelaufen, versuche es später nochmal oder melde dich bei mir!";
            }
        }));


        http.awaitInitialization();

        started.set(true);
        MarkdownConfig.PORT = http.port();
        lock.unlock();

        logger.info("Address: http://" + MarkdownConfig.HOST + ":" + MarkdownConfig.PORT);
        logger.info("Directory to be served: " + rd.getAbsolutePath());
    }

    private void fillNavbar(Map<String, Object> model, Request request, Response response) {
        model.put("id", request.session(true).id());
        model.put("username", request.session(true).attribute("username"));
        model.put("servername", MarkdownConfig.SERVER_NAME);
        model.put("dropdownName", MarkdownConfig.DROPDOWN_NAME);
        model.put("dropdownContent", mdManager.getDropdown());
        boolean admin = users.isAdmin(request);
        model.put("notAdminDisabled", admin ? "active" : "disabled");
        model.put("notAdminElement", admin ? "" : "aria-disabled=\"true\"");

    }

    private boolean tryLogin(Request request, Response response) {

        String username = request.queryParams("username");
        String password = request.queryParams("password");

        if (username == null || password == null) {
            return false;
        }

        if (!users.verifyLogin(username, password)) { //Invalid password
            return false;
        } else {
            logger.info("Login successful for " + username);
            response.cookie("login-id", sessionsManager.createSession(IpUtils.getIp(request), username), Math.toIntExact(MarkdownConfig.COOKIE_AGE / 1000L));
            request.session(true).attribute("username", username);
            return true;
        }
    }

    private boolean verifyLogin(Request request) {
        String cookie = request.cookie("login-id");
        if (cookie == null) {
            logger.info("No cookie found when accessing " + request.pathInfo());
            return false;
        }

        if (sessionsManager.hasSession(cookie)) {
            request.session(true).attribute("username", sessionsManager.getNameById(cookie));
            return true;
        }
        return false;
    }

    private void logout(Request request, Response response) {
        String cookie = request.cookie("login-id");
        sessionsManager.invalidateId(cookie);
        response.removeCookie("login-id");
    }

    public URI getUri() {
        String uri = MarkdownConfig.PORT != 80 ? "http://" + MarkdownConfig.HOST + ":" + MarkdownConfig.PORT : "http://" + MarkdownConfig.HOST;
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            logger.error("Could not create URI: " + uri, e);
            return null;
        }
    }
}

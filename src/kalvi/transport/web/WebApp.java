package kalvi.transport.web;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import kalvi.transport.AppDir;
import kalvi.transport.auth.AuthService;
import kalvi.transport.dao.*;
import kalvi.transport.service.TransportService;

/**
 * Entry point of the Java backend.
 *   java -cp "out:lib/*" kalvi.transport.web.WebApp [db.properties] [--port 8080] [--demo] [--mysql-only]
 * Tries MySQL first; if it cannot connect it falls back to data/demo files (unless --mysql-only).
 */
public class WebApp {
    public static final String VERSION = "2.1 (login + street map)";
    public static void main(String[] args) throws Exception {
        String props = AppDir.of("db.properties").toString();
        int port = 8080;
        boolean demo = false, mysqlOnly = false, noBrowser = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--demo" -> demo = true;
                case "--mysql-only" -> mysqlOnly = true;
                case "--no-browser" -> noBrowser = true;
                default -> { if (!args[i].startsWith("--")) props = args[i]; }
            }
        }

        banner();
        if (!demo) DriverLoader.ensure();
        TransportDao dao = null;
        String dbNote = "";
        if (!demo) {
            DbConfig cfg = null;
            Exception err = null;
            boolean passwordChanged = false;
            try { cfg = DbConfig.load(props); } catch (java.io.IOException e) { err = e; }
            for (int attempt = 0; cfg != null && attempt < 3 && dao == null; attempt++) {
                try { dao = connectOrSetup(cfg); err = null; }
                catch (Exception e) {
                    err = e;
                    if (!accessDenied(e) || System.console() == null) break;           // only ask when a person is at the keyboard
                    System.out.println("[db] MySQL refused the password for user '" + cfg.user + "'.");
                    char[] pw = System.console().readPassword("     Type your MySQL password (XAMPP default is empty: just press Enter; type skip for demo mode): ");
                    if (pw == null || new String(pw).equalsIgnoreCase("skip")) break;
                    cfg = cfg.withPassword(new String(pw));
                    passwordChanged = true;
                }
            }
            if (dao != null && cfg != null && passwordChanged) {
                try { DbConfig.savePassword(props, cfg.password); System.out.println("[db] Password saved in db.properties - you will not be asked again."); }
                catch (java.io.IOException e) { System.err.println("[db] Could not update db.properties: " + e.getMessage()); }
            }
            if (dao == null) {
                dbNote = explain(err, cfg);
                System.err.println("[db] MySQL not used: " + dbNote);
                if (mysqlOnly) System.exit(1);
            }
        }
        if (dao == null) {
            dao = new DemoTransportDao(AppDir.of("data", "demo"));
            System.out.println("[db] RUNNING IN DEMO MODE (reading data/demo files, nothing is saved to MySQL).");
        } else System.out.println("[db] Connected to MySQL.");

        java.util.concurrent.atomic.AtomicReference<TransportDao> daoRef = new java.util.concurrent.atomic.AtomicReference<>(dao);
        dao = Switchable.wrap(daoRef);                 // lets the web page switch demo -> MySQL while running
        TransportService service = new TransportService(dao);
        AuthService auth = new AuthService(dao);
        auth.seedDefaultAdmin();
        HttpServer server = null;
        for (int tryPort = port; tryPort < port + 30 && server == null; tryPort++) {      // an older copy may still hold the port
            try { server = HttpServer.create(new InetSocketAddress(tryPort), 0); if (tryPort != port) System.out.println("[web] Port " + port + " is busy (an older copy is probably still running) - using " + tryPort + " instead."); port = tryPort; }
            catch (java.net.BindException busy) { /* try the next one */ }
        }
        if (server == null) { System.err.println("No free port between " + port + " and " + (port + 29) + ". Close the old CampusTransit window and try again."); System.exit(1); }
        server.createContext("/api/", new ApiHandler(service, auth, new DbAdmin(daoRef, props, DbConfig.loadOrDefault(props), dbNote, service)));
        Path webDir = AppDir.of("web");
        server.createContext("/", new StaticHandler(webDir));
        server.setExecutor(Executors.newSingleThreadExecutor());   // one request at a time: simple + safe for one JDBC connection
        TransportDao finalDao = dao;
        Runtime.getRuntime().addShutdownHook(new Thread(finalDao::close));
        server.start();
        String url = "http://localhost:" + port;
        System.out.println("CampusTransit running at " + url + "   (" + dao.mode() + ")");
        System.out.println("Sign in with  admin / Admin@123  or choose 'Create an account'.   Press Ctrl+C to stop.");
        if (!noBrowser) openBrowser(url);
    }

    /** Connects; if the database or tables are missing, builds them from sql/ and connects again. */
    static TransportDao connectOrSetup(DbConfig cfg) throws Exception {
        try { return connect(cfg); }
        catch (Exception first) {
            if (!missingSchema(first)) throw first;
            System.out.println("[db] Database not set up yet - creating college_transport_db and loading the sample data ...");
            DbSetup.run(cfg, false);
            return connect(cfg);
        }
    }

    private static boolean accessDenied(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause())
            if (t instanceof java.sql.SQLException se && (se.getErrorCode() == 1045 || String.valueOf(se.getMessage()).contains("Access denied"))) return true;
        return false;
    }

    private static TransportDao connect(DbConfig cfg) throws Exception {
        TransportDao d = new JdbcTransportDao(cfg);
        d.stops();                                   // proves the schema is there
        return d;
    }

    /** MySQL error 1049 = unknown database, 1146 = table missing. */
    private static boolean missingSchema(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause())
            if (t instanceof java.sql.SQLException se && (se.getErrorCode() == 1049 || se.getErrorCode() == 1146)) return true;
        return false;
    }

    /** Turns a raw JDBC failure into one plain sentence that says what to do. */
    static String explain(Throwable e, DbConfig cfg) {
        String all = "";
        for (Throwable t = e; t != null; t = t.getCause()) all += " " + t;
        String user = cfg == null ? "root" : cfg.user;
        if (all.contains("ClassNotFoundException") || all.contains("No suitable driver") || all.contains("com.mysql"))
            return "The MySQL driver (mysql-connector-j jar) is missing from lib/. The program downloads it by itself when the computer is online - connect to the internet and press Connect again, or copy mysql-connector-j-8.x.x.jar into the lib folder.";
        if (all.contains("Access denied"))
            return "MySQL refused the login: wrong user or password in db.properties (it is trying user '" + user + "'). Fix the password there and restart.";
        if (all.contains("Connection refused") || all.contains("CommunicationsException") || all.contains("communications link"))
            return "MySQL is not running on this computer (nothing is listening on the port in db.properties). Start the MySQL service (Services / XAMPP / Workbench) and restart.";
        if (e instanceof java.io.FileNotFoundException) return "db.properties was not found next to run.sh / run.bat.";
        return "Could not use MySQL: " + (e == null ? "unknown error" : e.getMessage() == null ? e.toString() : e.getMessage());
    }

    private static void openBrowser(String url) {
        try {
            if (java.awt.GraphicsEnvironment.isHeadless() || !java.awt.Desktop.isDesktopSupported()
                    || !java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) return;
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (java.io.IOException | SecurityException | IllegalArgumentException ignored) { /* the URL is printed anyway */ }
    }

    private static void banner() {
        System.out.println("==============================================");
        System.out.println("   CampusTransit - Smart College Transport   v" + VERSION);
        System.out.println("==============================================");
    }
}
